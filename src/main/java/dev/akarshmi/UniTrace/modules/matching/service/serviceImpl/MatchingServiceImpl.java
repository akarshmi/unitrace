package dev.akarshmi.UniTrace.modules.matching.service.serviceImpl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akarshmi.UniTrace.modules.configs.MatchingProperties;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import dev.akarshmi.UniTrace.modules.item.repository.ItemDistanceProjection;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.item.repository.ItemVectorRepository;
import dev.akarshmi.UniTrace.modules.item.util.VectorUtils;
import dev.akarshmi.UniTrace.modules.matching.event.MatchCreatedEvent;
import dev.akarshmi.UniTrace.modules.matching.repository.MatchRepository;
import dev.akarshmi.UniTrace.modules.matching.service.MatchingService;
import dev.akarshmi.UniTrace.modules.matching.utill.TimeScoringUtils;

/**
 * finalScore = wText*text + wImage*image + wTime*time, every component in [0,1], higher is better.
 * <ul>
 *   <li>text  = 1 - cosine distance of MiniLM vectors (pgvector returns a distance, converted here).</li>
 *   <li>image = CLIP cosine rescaled from [floor, ceiling] to [0,1] (raw CLIP cosines are high for unrelated
 *       photos); neutral fallback when either item has no image.</li>
 *   <li>time  = {@link TimeScoringUtils}; neutral when dates are missing.</li>
 * </ul>
 * Guards against accidental matches: minimum text similarity gate, impossible-time rejection, threshold.
 * Location is intentionally not part of the score yet (see project notes on campus blocks).
 */
@Service
public class MatchingServiceImpl implements MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingServiceImpl.class);

    private final ItemRepository itemRepository;
    private final ItemVectorRepository vectors;
    private final MatchRepository matches;
    private final ApplicationEventPublisher events;
    private final MatchingProperties props;
    private final Clock clock;

    @Autowired
    public MatchingServiceImpl(ItemRepository itemRepository,
                               ItemVectorRepository vectors,
                               MatchRepository matches,
                               ApplicationEventPublisher events,
                               MatchingProperties props) {
        this(itemRepository, vectors, matches, events, props, Clock.systemUTC());
    }

    MatchingServiceImpl(ItemRepository itemRepository,
                        ItemVectorRepository vectors,
                        MatchRepository matches,
                        ApplicationEventPublisher events,
                        MatchingProperties props,
                        Clock clock) {
        this.itemRepository = itemRepository;
        this.vectors = vectors;
        this.matches = matches;
        this.events = events;
        this.props = props;
        this.clock = clock;
    }

    @Override
    @Transactional
    public List<ScoredMatch> findAndRecordMatches(UUID itemId) {
        Item item = itemRepository.findById(itemId).orElse(null);
        if (item == null) {
            log.warn("Matching skipped: item {} not found", itemId);
            return List.of();
        }
        if (!isEligible(item)) {
            log.debug("Matching skipped: item {} is not eligible (type/status)", itemId);
            return List.of();
        }
        float[] textVector = item.getTextEmbedding();
        if (!VectorUtils.isUsable(textVector)) {
            log.warn("Matching skipped: item {} has no usable text embedding", itemId);
            return List.of();
        }

        ItemType opposite = item.getType() == ItemType.LOST ? ItemType.FOUND : ItemType.LOST;
        List<ItemDistanceProjection> rows = vectors.findCandidateDistances(
                VectorUtils.toVectorLiteral(textVector), opposite.name(), props.eligibleStatuses(),
                item.getId(), props.candidateLimit());
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> distances = new HashMap<>();
        for (ItemDistanceProjection row : rows) {
            distances.put(row.getId(), row.getDistance());
        }
        Map<UUID, Item> candidates = new HashMap<>();
        for (Item c : itemRepository.findAllById(distances.keySet())) {
            candidates.put(c.getId(), c);
        }

        Instant now = clock.instant();
        List<ScoredMatch> scored = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : distances.entrySet()) {
            Item candidate = candidates.get(entry.getKey());
            if (candidate == null || candidate.getType() != opposite || !isEligible(candidate)
                    || sameReporter(item, candidate)) {
                continue;
            }
            ScoredMatch s = score(item, candidate, entry.getValue(), now);
            if (s != null && s.finalScore() >= props.threshold()) {
                scored.add(s);
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredMatch::finalScore).reversed());
        List<ScoredMatch> top = scored.size() > props.maxMatchesPerItem()
                ? scored.subList(0, props.maxMatchesPerItem()) : scored;

        List<ScoredMatch> created = new ArrayList<>();
        for (ScoredMatch s : top) {
            if (matches.existsByLostItem_IdAndFoundItem_Id(s.lostItemId(), s.foundItemId())) {
                continue;
            }
            List<UUID> ids = matches.insertIfAbsent(s.lostItemId(), s.foundItemId(), s.textScore(),
                    s.imageCompared(), s.imageScore(), s.timeScore(), s.finalScore());
            if (ids.isEmpty()) {
                continue; // concurrent worker inserted the same pair first
            }
            ScoredMatch persisted = s.withMatchId(ids.get(0));
            created.add(persisted);
            events.publishEvent(new MatchCreatedEvent(
                    persisted.matchId(), s.lostItemId(), s.foundItemId(), s.finalScore()));
        }
        log.info("Item {}: {} candidate(s), {} above threshold, {} new match(es)",
                itemId, rows.size(), scored.size(), created.size());
        return created;
    }

    /** Returns null when the pair must not match (bad distance, text gate, impossible timeline, NaN). */
    ScoredMatch score(Item subject, Item candidate, Double textDistance, Instant now) {
        if (textDistance == null || textDistance.isNaN()) {
            return null;
        }
        double text = clamp01(1.0 - textDistance);
        if (text < props.minTextSimilarity()) {
            return null;
        }

        Item lost = subject.getType() == ItemType.LOST ? subject : candidate;
        Item found = lost == subject ? candidate : subject;

        OptionalDouble cosine = VectorUtils.safeCosine(subject.getImageEmbedding(), candidate.getImageEmbedding());
        boolean imageCompared = cosine.isPresent();
        double image = imageCompared
                ? clamp01((cosine.getAsDouble() - props.imageSimilarityFloor())
                        / (props.imageSimilarityCeiling() - props.imageSimilarityFloor()))
                : props.neutralImageScore();

        double time = TimeScoringUtils.timeScore(
                lost.getEventFrom(), lost.getEventTo(), found.getEventFrom(), found.getEventTo(), now,
                props.timeHalfLife(), props.timeMaxGap(), props.timeFoundBeforeLostTolerance());
        if (props.rejectImpossibleTime() && time <= 0.0) {
            return null;
        }

        double finalScore = props.textWeight() * text + props.imageWeight() * image + props.timeWeight() * time;
        if (Double.isNaN(finalScore) || Double.isInfinite(finalScore)) {
            return null;
        }
        return new ScoredMatch(null, lost.getId(), found.getId(), text, imageCompared, image, time, clamp01(finalScore));
    }

    /** A user's own lost and found reports must never match each other. */
    private static boolean sameReporter(Item a, Item b) {
        return a.getReportedBy() != null && b.getReportedBy() != null
                && a.getReportedBy().getId() != null
                && a.getReportedBy().getId().equals(b.getReportedBy().getId());
    }

    private boolean isEligible(Item item) {
        return item.getType() != null
                && item.getStatus() != null
                && props.eligibleStatuses().contains(item.getStatus().name());
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}
