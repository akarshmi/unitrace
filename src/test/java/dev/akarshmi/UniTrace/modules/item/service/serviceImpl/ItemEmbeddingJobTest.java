package dev.akarshmi.UniTrace.modules.item.service.serviceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import dev.akarshmi.UniTrace.modules.configs.EmbeddingJobProperties;
import dev.akarshmi.UniTrace.modules.exceptions.EmbeddingException;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.item.repository.ItemVectorRepository;
import dev.akarshmi.UniTrace.modules.item.service.EmbeddingService;
import dev.akarshmi.UniTrace.modules.matching.service.MatchingService;

@ExtendWith(MockitoExtension.class)
class ItemEmbeddingJobTest {

    @Mock ItemRepository itemRepository;
    @Mock ItemVectorRepository vectors;
    @Mock EmbeddingService embeddingService;
    @Mock MatchingService matchingService;

    private final EmbeddingJobProperties props =
            new EmbeddingJobProperties(5, 3, Duration.ofMinutes(10), Duration.ofSeconds(30), Duration.ofMinutes(30));
    private ItemEmbeddingJob job;

    private static final float[] TEXT = {1f, 0f};
    private static final float[] IMG = {0f, 1f};

    @BeforeEach
    void setUp() {
        job = new ItemEmbeddingJob(itemRepository, vectors, embeddingService, matchingService, props);
    }

    private Item item(UUID id, String title, String desc, String imageUrl, Integer attempts) {
        Item i = mock(Item.class, withSettings().strictness(Strictness.LENIENT));
        when(i.getId()).thenReturn(id);
        when(i.getTitle()).thenReturn(title);
        when(i.getDescription()).thenReturn(desc);
        when(i.getImageUrl()).thenReturn(imageUrl);
        when(i.getEmbeddingAttempts()).thenReturn(attempts);
        when(itemRepository.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    @Test
    void successStoresBothVectorsAndTriggersMatching() {
        UUID id = UUID.randomUUID();
        item(id, "Backpack", "Black with red zip", "https://res.cloudinary.com/a.jpg", 1);
        when(embeddingService.embedText("Backpack. Black with red zip")).thenReturn(TEXT);
        when(embeddingService.embedImage("https://res.cloudinary.com/a.jpg")).thenReturn(IMG);
        when(vectors.markReady(eq(id), eq("[1.0,0.0]"), eq("[0.0,1.0]"), any(Instant.class))).thenReturn(1);

        job.processClaimed(id);

        verify(matchingService).findAndRecordMatches(id);
        verify(vectors, never()).markRetry(any(), any(), anyString());
        verify(vectors, never()).markFailed(any(), anyString());
    }

    @Test
    void itemWithoutImageIsStoredTextOnly() {
        UUID id = UUID.randomUUID();
        item(id, "Keys", "", null, 1);
        when(embeddingService.embedText("Keys")).thenReturn(TEXT);
        when(vectors.markReadyTextOnly(eq(id), anyString(), any(Instant.class))).thenReturn(1);

        job.processClaimed(id);

        verify(embeddingService, never()).embedImage(anyString());
        verify(matchingService).findAndRecordMatches(id);
    }

    @Test
    void permanentImageFailureDegradesToTextOnly() {
        UUID id = UUID.randomUUID();
        item(id, "Wallet", "Brown", "https://res.cloudinary.com/bad.jpg", 1);
        when(embeddingService.embedText(anyString())).thenReturn(TEXT);
        when(embeddingService.embedImage(anyString())).thenThrow(EmbeddingException.permanent("not an image"));
        when(vectors.markReadyTextOnly(eq(id), anyString(), any(Instant.class))).thenReturn(1);

        job.processClaimed(id);

        verify(vectors).markReadyTextOnly(eq(id), anyString(), any(Instant.class));
        verify(matchingService).findAndRecordMatches(id);
    }

    @Test
    void transientImageFailureRetriesWholeItem() {
        UUID id = UUID.randomUUID();
        item(id, "Wallet", "Brown", "https://res.cloudinary.com/a.jpg", 1);
        when(embeddingService.embedText(anyString())).thenReturn(TEXT);
        when(embeddingService.embedImage(anyString())).thenThrow(EmbeddingException.transientError("timeout", null));

        job.processClaimed(id);

        verify(vectors).markRetry(eq(id), any(Instant.class), anyString());
        verify(vectors, never()).markReady(any(), anyString(), anyString(), any());
        verifyNoInteractions(matchingService);
    }

    @Test
    void transientFailureRetriesUntilAttemptsExhausted() {
        UUID first = UUID.randomUUID(), last = UUID.randomUUID();
        item(first, "A", "b", null, 1);
        item(last, "A", "b", null, 3); // == maxAttempts
        when(embeddingService.embedText(anyString())).thenThrow(EmbeddingException.transientError("503", null));

        job.processClaimed(first);
        job.processClaimed(last);

        verify(vectors).markRetry(eq(first), any(Instant.class), anyString());
        verify(vectors).markFailed(eq(last), anyString());
        verifyNoInteractions(matchingService);
    }

    @Test
    void permanentFailureFailsImmediately() {
        UUID id = UUID.randomUUID();
        item(id, "A", "b", null, 1);
        when(embeddingService.embedText(anyString())).thenThrow(EmbeddingException.permanent("bad"));

        job.processClaimed(id);

        verify(vectors).markFailed(eq(id), anyString());
        verify(vectors, never()).markRetry(any(), any(), anyString());
    }

    @Test
    void blankContentFailsWithoutCallingTheModel() {
        UUID id = UUID.randomUUID();
        item(id, " ", null, null, 1);

        job.processClaimed(id);

        verifyNoInteractions(embeddingService);
        verify(vectors).markFailed(eq(id), anyString());
    }

    @Test
    void lostLeaseDiscardsResultAndSkipsMatching() {
        UUID id = UUID.randomUUID();
        item(id, "A", "b", null, 1);
        when(embeddingService.embedText(anyString())).thenReturn(TEXT);
        when(vectors.markReadyTextOnly(eq(id), anyString(), any(Instant.class))).thenReturn(0);

        job.processClaimed(id);

        verifyNoInteractions(matchingService);
    }

    @Test
    void matchingFailureDoesNotUndoEmbedding() {
        UUID id = UUID.randomUUID();
        item(id, "A", "b", null, 1);
        when(embeddingService.embedText(anyString())).thenReturn(TEXT);
        when(vectors.markReadyTextOnly(eq(id), anyString(), any(Instant.class))).thenReturn(1);
        doThrow(new IllegalStateException("db down")).when(matchingService).findAndRecordMatches(id);

        assertDoesNotThrow(() -> job.processClaimed(id));
        verify(vectors, never()).markFailed(any(), anyString());
    }

    @Test
    void pollProcessesEveryLeasedItem() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        item(a, "A", "x", null, 1);
        item(b, "B", "y", null, 1);
        when(vectors.claimBatch(any(Instant.class), any(Instant.class), eq(5))).thenReturn(List.of(a, b));
        when(embeddingService.embedText(anyString())).thenReturn(TEXT);
        when(vectors.markReadyTextOnly(any(), anyString(), any(Instant.class))).thenReturn(1);

        job.poll();

        verify(matchingService, times(2)).findAndRecordMatches(any());
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertEquals(Duration.ofSeconds(30), job.backoff(1));
        assertEquals(Duration.ofSeconds(60), job.backoff(2));
        assertEquals(Duration.ofSeconds(120), job.backoff(3));
        assertEquals(Duration.ofMinutes(30), job.backoff(30));
    }

    @Test
    void embeddingTextCombinesAndTruncates() {
        Item i = mock(Item.class);
        when(i.getTitle()).thenReturn("T");
        when(i.getDescription()).thenReturn("D".repeat(2000));
        String text = ItemEmbeddingJob.buildEmbeddingText(i);
        assertTrue(text.startsWith("T. D"));
        assertEquals(1000, text.length());
    }
}
