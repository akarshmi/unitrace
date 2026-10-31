package dev.akarshmi.UniTrace.modules.item.service.serviceImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.akarshmi.UniTrace.modules.configs.EmbeddingJobProperties;
import dev.akarshmi.UniTrace.modules.exceptions.EmbeddingException;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.event.ItemCreatedEvent;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.item.repository.ItemVectorRepository;
import dev.akarshmi.UniTrace.modules.item.service.EmbeddingService;
import dev.akarshmi.UniTrace.modules.item.util.VectorUtils;
import dev.akarshmi.UniTrace.modules.matching.service.MatchingService;

/**
 * Generates embeddings for items and then triggers matching.
 *
 * <ul>
 *   <li>Fast path: {@link ItemCreatedEvent} (after commit) processes the new item immediately.</li>
 *   <li>Safety net: a poller leases due items (PENDING / RETRY / expired lease) using FOR UPDATE SKIP LOCKED.</li>
 *   <li>No transaction is held during HTTP calls; state changes are single guarded UPDATEs
 *       ({@code WHERE embedding_status = 'PROCESSING'}) so a stale worker can never overwrite a valid result.</li>
 *   <li>Transient failures retry with exponential backoff up to max-attempts; permanent ones fail immediately.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "unitrace.embedding.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ItemEmbeddingJob {

    private static final Logger log = LoggerFactory.getLogger(ItemEmbeddingJob.class);
    private static final int MAX_TEXT_CHARS = 1000;
    private static final int MAX_ERROR_CHARS = 500;

    private final ItemRepository itemRepository;
    private final ItemVectorRepository vectors;
    private final EmbeddingService embeddingService;
    private final MatchingService matchingService;
    private final EmbeddingJobProperties props;

    public ItemEmbeddingJob(ItemRepository itemRepository,
                            ItemVectorRepository vectors,
                            EmbeddingService embeddingService,
                            MatchingService matchingService,
                            EmbeddingJobProperties props) {
        this.itemRepository = itemRepository;
        this.vectors = vectors;
        this.embeddingService = embeddingService;
        this.matchingService = matchingService;
        this.props = props;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemCreated(ItemCreatedEvent event) {
        Instant now = Instant.now();
        List<UUID> claimed = vectors.claimById(event.itemId(), now, now.plus(props.leaseDuration()));
        if (!claimed.isEmpty()) {
            processClaimed(event.itemId());
        }
    }

    @Scheduled(fixedDelayString = "${unitrace.embedding.job.poll-interval:PT5S}")
    public void poll() {
        Instant now = Instant.now();
        List<UUID> ids = vectors.claimBatch(now, now.plus(props.leaseDuration()), props.batchSize());
        for (UUID id : ids) {
            processClaimed(id);
        }
    }

    /** Package-private for tests: the item is already leased (status PROCESSING) by this worker. */
    void processClaimed(UUID id) {
        Item item = itemRepository.findById(id).orElse(null);
        if (item == null) {
            log.warn("Leased item {} no longer exists; skipping", id);
            return;
        }
        int attempts = item.getEmbeddingAttempts() == null ? 1 : item.getEmbeddingAttempts();

        boolean ready;
        try {
            ready = embedAndStore(item);
        } catch (EmbeddingException e) {
            fail(id, attempts, e.isTransientFailure(), e.getMessage());
            return;
        } catch (RuntimeException e) {
            fail(id, attempts, true, e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (ready) {
            runMatching(id);
        }
    }

    private boolean embedAndStore(Item item) {
        UUID id = item.getId();
        String text = buildEmbeddingText(item);
        if (text.isBlank()) {
            throw EmbeddingException.permanent("Item has no title or description to embed");
        }

        float[] textVector = embeddingService.embedText(text);
        float[] imageVector = null;

        String imageUrl = item.getImageUrl();
        if (imageUrl != null && !imageUrl.isBlank()) {
            try {
                imageVector = embeddingService.embedImage(imageUrl);
            } catch (EmbeddingException e) {
                if (e.isTransientFailure()) {
                    throw e; // retry the whole item rather than silently losing the image signal
                }
                log.warn("Image embedding permanently failed for item {}; continuing text-only: {}", id, e.getMessage());
            }
        }

        Instant now = Instant.now();
        int updated = imageVector == null
                ? vectors.markReadyTextOnly(id, VectorUtils.toVectorLiteral(textVector), now)
                : vectors.markReady(id, VectorUtils.toVectorLiteral(textVector),
                        VectorUtils.toVectorLiteral(imageVector), now);
        if (updated == 0) {
            log.warn("Item {} was no longer leased by this worker; discarding result", id);
            return false;
        }
        return true;
    }

    private void fail(UUID id, int attempts, boolean transientFailure, String message) {
        String error = truncate(message);
        if (transientFailure && attempts < props.maxAttempts()) {
            Instant next = Instant.now().plus(backoff(attempts));
            vectors.markRetry(id, next, error);
            log.warn("Embedding for item {} failed (attempt {}/{}), retry at {}: {}",
                    id, attempts, props.maxAttempts(), next, error);
        } else {
            vectors.markFailed(id, error);
            log.error("Embedding for item {} failed permanently after {} attempt(s): {}", id, attempts, error);
        }
    }

    private void runMatching(UUID id) {
        try {
            matchingService.findAndRecordMatches(id);
        } catch (RuntimeException e) {
            // Embeddings are already saved; matching is idempotent so a later run is safe.
            log.error("Matching failed for item {}", id, e);
        }
    }

    Duration backoff(int attempts) {
        int shift = Math.min(Math.max(attempts - 1, 0), 20);
        Duration d = props.backoffBase().multipliedBy(1L << shift);
        return d.compareTo(props.backoffMax()) > 0 ? props.backoffMax() : d;
    }

    static String buildEmbeddingText(Item item) {
        String title = item.getTitle() == null ? "" : item.getTitle().strip();
        String description = item.getDescription() == null ? "" : item.getDescription().strip();
        String combined = (title.isEmpty() || description.isEmpty()) ? title + description : title + ". " + description;
        return combined.length() > MAX_TEXT_CHARS ? combined.substring(0, MAX_TEXT_CHARS) : combined;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "unknown error";
        }
        return s.length() > MAX_ERROR_CHARS ? s.substring(0, MAX_ERROR_CHARS) : s;
    }
}
