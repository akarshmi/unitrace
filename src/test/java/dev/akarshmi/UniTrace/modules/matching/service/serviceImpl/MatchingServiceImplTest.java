package dev.akarshmi.UniTrace.modules.matching.service.serviceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import dev.akarshmi.UniTrace.modules.configs.MatchingProperties;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import dev.akarshmi.UniTrace.modules.item.repository.ItemDistanceProjection;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.item.repository.ItemVectorRepository;
import dev.akarshmi.UniTrace.modules.matching.event.MatchCreatedEvent;
import dev.akarshmi.UniTrace.modules.matching.repository.MatchRepository;
import dev.akarshmi.UniTrace.modules.matching.service.MatchingService.ScoredMatch;
import dev.akarshmi.UniTrace.modules.user.entity.User;

@ExtendWith(MockitoExtension.class)
class MatchingServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final float[] TEXT = {1f, 0f, 0f};
    private static final float[] IMG = {1f, 0f};
    private static final ItemStatus ACTIVE = ItemStatus.values()[0];

    @Mock ItemRepository itemRepository;
    @Mock ItemVectorRepository vectors;
    @Mock MatchRepository matches;
    @Mock ApplicationEventPublisher events;

    private MatchingProperties props;
    private MatchingServiceImpl service;

    record Row(UUID id, Double distance) implements ItemDistanceProjection {
        public UUID getId() { return id; }
        public Double getDistance() { return distance; }
    }

    @BeforeEach
    void setUp() {
        props = new MatchingProperties(0.5, 0.35, 0.15, 0.55, 20, 10, 0.30, 0.5, 0.60, 0.95,
                List.of(ACTIVE.name()), Duration.ofHours(48), Duration.ofDays(14), Duration.ofHours(2), true);
        service = new MatchingServiceImpl(itemRepository, vectors, matches, events, props,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Item item(UUID id, ItemType type, float[] text, float[] image, Instant from, Instant to, ItemStatus status) {
        Item i = mock(Item.class, withSettings().strictness(Strictness.LENIENT));
        when(i.getId()).thenReturn(id);
        when(i.getType()).thenReturn(type);
        when(i.getStatus()).thenReturn(status);
        when(i.getTextEmbedding()).thenReturn(text);
        when(i.getImageEmbedding()).thenReturn(image);
        when(i.getEventFrom()).thenReturn(from);
        when(i.getEventTo()).thenReturn(to);
        return i;
    }

    private Item active(UUID id, ItemType type, float[] image, Instant from, Instant to) {
        return item(id, type, TEXT, image, from, to, ACTIVE);
    }

    @Test
    void foundItemSearchesLostCandidatesAndPersistsMatch() {
        UUID foundId = UUID.randomUUID(), lostId = UUID.randomUUID(), matchId = UUID.randomUUID();
        Item found = active(foundId, ItemType.FOUND, IMG, NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(1)));
        Item lost = active(lostId, ItemType.LOST, IMG, NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(2)));

        when(itemRepository.findById(foundId)).thenReturn(Optional.of(found));
        when(vectors.findCandidateDistances(anyString(), eq("LOST"), any(), eq(foundId), eq(20)))
                .thenReturn(List.of(new Row(lostId, 0.10)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(lost));
        when(matches.insertIfAbsent(eq(lostId), eq(foundId), anyDouble(), eq(true), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(matchId));

        List<ScoredMatch> created = service.findAndRecordMatches(foundId);

        assertEquals(1, created.size());
        assertEquals(lostId, created.get(0).lostItemId());
        assertEquals(foundId, created.get(0).foundItemId());
        assertEquals(matchId, created.get(0).matchId());
        assertTrue(created.get(0).finalScore() > 0.9);
        verify(events).publishEvent(any(MatchCreatedEvent.class));
    }

    @Test
    void lostItemSearchesFoundCandidates() {
        UUID lostId = UUID.randomUUID();
        Item lost = active(lostId, ItemType.LOST, null, null, null);
        when(itemRepository.findById(lostId)).thenReturn(Optional.of(lost));
        when(vectors.findCandidateDistances(anyString(), eq("FOUND"), any(), eq(lostId), anyInt()))
                .thenReturn(List.of());

        assertTrue(service.findAndRecordMatches(lostId).isEmpty());
        verify(vectors).findCandidateDistances(anyString(), eq("FOUND"), any(), eq(lostId), anyInt());
    }

    @Test
    void sameTypeCandidatesAreIgnored() {
        UUID id = UUID.randomUUID(), otherId = UUID.randomUUID();
        Item subject = active(id, ItemType.LOST, IMG, null, null);
        Item sameType = active(otherId, ItemType.LOST, IMG, null, null);
        when(itemRepository.findById(id)).thenReturn(Optional.of(subject));
        when(vectors.findCandidateDistances(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(new Row(otherId, 0.05)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(sameType));

        assertTrue(service.findAndRecordMatches(id).isEmpty());
        verify(matches, never()).insertIfAbsent(any(), any(), anyDouble(), anyBoolean(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void belowThresholdIsNotPersisted() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Item found = active(a, ItemType.FOUND, null, NOW.minusSeconds(3600), NOW.minusSeconds(3600));
        Item lost = active(b, ItemType.LOST, null, NOW.minusSeconds(7200), NOW.minusSeconds(7000));
        when(itemRepository.findById(a)).thenReturn(Optional.of(found));
        when(vectors.findCandidateDistances(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(new Row(b, 0.65))); // text 0.35: passes the gate, final < 0.55
        when(itemRepository.findAllById(any())).thenReturn(List.of(lost));

        assertTrue(service.findAndRecordMatches(a).isEmpty());
        verify(matches, never()).insertIfAbsent(any(), any(), anyDouble(), anyBoolean(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void existingPairIsSkipped() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Item found = active(a, ItemType.FOUND, IMG, null, null);
        Item lost = active(b, ItemType.LOST, IMG, null, null);
        when(itemRepository.findById(a)).thenReturn(Optional.of(found));
        when(vectors.findCandidateDistances(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(new Row(b, 0.05)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(lost));
        when(matches.existsByLostItem_IdAndFoundItem_Id(b, a)).thenReturn(true);

        assertTrue(service.findAndRecordMatches(a).isEmpty());
        verify(matches, never()).insertIfAbsent(any(), any(), anyDouble(), anyBoolean(), anyDouble(), anyDouble(), anyDouble());
        verifyNoInteractions(events);
    }

    @Test
    void concurrentDuplicateInsertPublishesNoEvent() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        Item found = active(a, ItemType.FOUND, IMG, null, null);
        Item lost = active(b, ItemType.LOST, IMG, null, null);
        when(itemRepository.findById(a)).thenReturn(Optional.of(found));
        when(vectors.findCandidateDistances(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(new Row(b, 0.05)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(lost));
        when(matches.insertIfAbsent(any(), any(), anyDouble(), anyBoolean(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of()); // ON CONFLICT DO NOTHING

        assertTrue(service.findAndRecordMatches(a).isEmpty());
        verifyNoInteractions(events);
    }

    @Test
    void ineligibleOrInvalidItemsDoNothing() {
        UUID id = UUID.randomUUID();
        ItemStatus other = ItemStatus.values().length > 1 ? ItemStatus.values()[1] : null;
        Item notActive = item(id, ItemType.LOST, TEXT, IMG, null, null, other);
        when(itemRepository.findById(id)).thenReturn(Optional.of(notActive));
        assertTrue(service.findAndRecordMatches(id).isEmpty());

        UUID bad = UUID.randomUUID();
        Item nan = item(bad, ItemType.LOST, new float[]{Float.NaN, 1f}, null, null, null, ACTIVE);
        when(itemRepository.findById(bad)).thenReturn(Optional.of(nan));
        assertTrue(service.findAndRecordMatches(bad).isEmpty());

        UUID missing = UUID.randomUUID();
        when(itemRepository.findById(missing)).thenReturn(Optional.empty());
        assertTrue(service.findAndRecordMatches(missing).isEmpty());

        verifyNoInteractions(vectors);
    }

    @Test
    void ranksByFinalScoreAndHonoursMaxMatches() {
        props = new MatchingProperties(0.5, 0.35, 0.15, 0.55, 20, 1, 0.30, 0.5, 0.60, 0.95,
                List.of(ACTIVE.name()), Duration.ofHours(48), Duration.ofDays(14), Duration.ofHours(2), true);
        service = new MatchingServiceImpl(itemRepository, vectors, matches, events, props, Clock.fixed(NOW, ZoneOffset.UTC));

        UUID found = UUID.randomUUID(), good = UUID.randomUUID(), better = UUID.randomUUID();
        Item f = active(found, ItemType.FOUND, IMG, null, null);
        Item l1 = active(good, ItemType.LOST, IMG, null, null);
        Item l2 = active(better, ItemType.LOST, IMG, null, null);
        when(itemRepository.findById(found)).thenReturn(Optional.of(f));
        when(vectors.findCandidateDistances(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(new Row(good, 0.30), new Row(better, 0.05)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(l1, l2));
        when(matches.insertIfAbsent(any(), any(), anyDouble(), anyBoolean(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(UUID.randomUUID()));

        List<ScoredMatch> created = service.findAndRecordMatches(found);
        assertEquals(1, created.size());
        assertEquals(better, created.get(0).lostItemId());
    }

    @Test
    void ownLostAndFoundReportsNeverMatch() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        User owner = User.builder().id(UUID.randomUUID()).build();
        Item found = active(a, ItemType.FOUND, IMG, null, null);
        Item lost = active(b, ItemType.LOST, IMG, null, null);
        when(found.getReportedBy()).thenReturn(owner);
        when(lost.getReportedBy()).thenReturn(owner);
        when(itemRepository.findById(a)).thenReturn(Optional.of(found));
        when(vectors.findCandidateDistances(anyString(), anyString(), any(), any(), anyInt()))
                .thenReturn(List.of(new Row(b, 0.05)));
        when(itemRepository.findAllById(any())).thenReturn(List.of(lost));

        assertTrue(service.findAndRecordMatches(a).isEmpty());
        verify(matches, never()).insertIfAbsent(any(), any(), anyDouble(), anyBoolean(), anyDouble(), anyDouble(), anyDouble());
    }

    // ---- scoring edge cases -------------------------------------------------------------------------------------

    @Test
    void missingImageUsesNeutralScoreAndMissingTimesAreNeutral() {
        Item found = active(UUID.randomUUID(), ItemType.FOUND, null, null, null);
        Item lost = active(UUID.randomUUID(), ItemType.LOST, IMG, null, null);

        ScoredMatch s = service.score(found, lost, 0.2, NOW);

        assertNotNull(s);
        assertFalse(s.imageCompared());
        assertEquals(0.5, s.imageScore(), 1e-9);
        assertEquals(0.5, s.timeScore(), 1e-9);
        assertEquals(0.5 * 0.8 + 0.35 * 0.5 + 0.15 * 0.5, s.finalScore(), 1e-9);
        assertEquals(lost.getId(), s.lostItemId());
        assertEquals(found.getId(), s.foundItemId());
    }

    @Test
    void textGateRejectsDissimilarPairs() {
        Item a = active(UUID.randomUUID(), ItemType.FOUND, IMG, null, null);
        Item b = active(UUID.randomUUID(), ItemType.LOST, IMG, null, null);
        assertNull(service.score(a, b, 0.9, NOW)); // text 0.1 < 0.30
        assertNull(service.score(a, b, null, NOW));
        assertNull(service.score(a, b, Double.NaN, NOW));
    }

    @Test
    void impossibleTimelineIsRejected() {
        Item found = active(UUID.randomUUID(), ItemType.FOUND, IMG, NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(2)));
        Item lost = active(UUID.randomUUID(), ItemType.LOST, IMG, NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(4)));
        assertNull(service.score(found, lost, 0.05, NOW));
    }

    @Test
    void imageSimilarityIsRescaledAndOrthogonalImagesScoreZero() {
        Item found = active(UUID.randomUUID(), ItemType.FOUND, new float[]{1f, 0f}, null, null);
        Item lost = active(UUID.randomUUID(), ItemType.LOST, new float[]{0f, 1f}, null, null);
        ScoredMatch s = service.score(found, lost, 0.1, NOW);
        assertNotNull(s);
        assertTrue(s.imageCompared());
        assertEquals(0.0, s.imageScore(), 1e-9);
    }
}
