package dev.akarshmi.UniTrace.modules.matching.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidHandoverKeyException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidMatchStateException;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;
import dev.akarshmi.UniTrace.modules.item.repository.ItemRepository;
import dev.akarshmi.UniTrace.modules.matching.dto.CompleteHandoverRequest;
import dev.akarshmi.UniTrace.modules.matching.dto.DeskMatchResponse;
import dev.akarshmi.UniTrace.modules.matching.entity.Match;
import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;
import dev.akarshmi.UniTrace.modules.matching.event.MatchApprovedEvent;
import dev.akarshmi.UniTrace.modules.matching.mapper.MatchMapper;
import dev.akarshmi.UniTrace.modules.matching.repository.MatchRepository;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class MatchModerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final String MOD_EMAIL = "desk@lpu.in";

    @Mock MatchRepository matches;
    @Mock ItemRepository items;
    @Mock UserRepository users;
    @Mock ApplicationEventPublisher events;

    private DeskProperties props;
    private HandoverKeyService keys;
    private MatchModerationService service;

    private User lostOwner, finder, moderator;
    private Item lost, found;
    private Match match;
    private final UUID matchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        props = new DeskProperties("test-secret", Duration.ofDays(7), 3, null, "Asia/Kolkata", "17:00");
        keys = new HandoverKeyService(props);
        service = new MatchModerationService(matches, items, users, keys,
                new MatchMapper(new DropOffPolicy(props)), events, props, Clock.fixed(NOW, ZoneOffset.UTC));

        lostOwner = User.builder().id(UUID.randomUUID()).registrationNumber(1001L)
                .firstName("Lia").lastName("Lost").uniEmail("lia@lpu.in").build();
        finder = User.builder().id(UUID.randomUUID()).registrationNumber(1002L)
                .firstName("Finn").lastName("Finder").uniEmail("finn@lpu.in").build();
        moderator = User.builder().id(UUID.randomUUID()).registrationNumber(9L)
                .firstName("Desk").uniEmail(MOD_EMAIL).build();

        lost = Item.builder().id(UUID.randomUUID()).type(ItemType.LOST).title("Blue bottle")
                .description("d").location("Block 32").reportedBy(lostOwner).build();
        found = Item.builder().id(UUID.randomUUID()).type(ItemType.FOUND).title("Bottle")
                .description("d").location("Block 34").reportedBy(finder).build();

        match = new Match();
        match.setLostItem(lost);
        match.setFoundItem(found);
        match.setFinalScore(0.9);

        when(users.findByUniEmail(MOD_EMAIL)).thenReturn(Optional.of(moderator));
        lenientMatch();
    }

    private void lenientMatch() {
        org.mockito.Mockito.lenient().when(matches.fetchById(any())).thenReturn(Optional.of(match));
        org.mockito.Mockito.lenient().when(items.findById(any())).thenReturn(Optional.of(found));
    }

    private String approveAndCaptureKey() {
        service.approve(matchId, MOD_EMAIL, "looks right");
        ArgumentCaptor<MatchApprovedEvent> cap = ArgumentCaptor.forClass(MatchApprovedEvent.class);
        verify(events).publishEvent(cap.capture());
        return cap.getValue().handoverKey();
    }

    @Test
    void approveIssuesKeyMarksItemsMatchedAndPublishesEvent() {
        ArgumentCaptor<MatchApprovedEvent> cap = ArgumentCaptor.forClass(MatchApprovedEvent.class);

        DeskMatchResponse res = service.approve(matchId, MOD_EMAIL, "ok");

        assertEquals(MatchStatus.APPROVED, res.status());
        assertEquals(ItemStatus.MATCHED, lost.getStatus());
        assertEquals(ItemStatus.MATCHED, found.getStatus());
        assertEquals(moderator.getId(), match.getReviewedBy());
        verify(events).publishEvent(cap.capture());
        MatchApprovedEvent e = cap.getValue();
        assertEquals("lia@lpu.in", e.lostOwnerEmail());
        assertEquals("finn@lpu.in", e.foundOwnerEmail());
        assertFalse(e.reissued());
        assertTrue(keys.matches(e.handoverKey(), match.getHandoverKeyHash()));
        assertEquals(NOW.plus(Duration.ofDays(7)), match.getHandoverKeyExpiresAt());
        assertFalse(e.toString().contains(e.handoverKey()));
    }

    @Test
    void approveFailsWhenAnItemIsNoLongerOpen() {
        found.markMatched();
        assertThrows(InvalidMatchStateException.class, () -> service.approve(matchId, MOD_EMAIL, null));
        assertEquals(MatchStatus.PENDING, match.getStatus());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void cannotApproveTwice() {
        service.approve(matchId, MOD_EMAIL, null);
        assertThrows(InvalidMatchStateException.class, () -> service.approve(matchId, MOD_EMAIL, null));
    }

    @Test
    void rejectStoresNoteAndLeavesItemsOpen() {
        DeskMatchResponse res = service.reject(matchId, MOD_EMAIL, "different brand");
        assertEquals(MatchStatus.REJECTED, res.status());
        assertEquals("different brand", match.getReviewNote());
        assertEquals(ItemStatus.OPEN, lost.getStatus());
        assertThrows(InvalidMatchStateException.class, () -> service.reject(matchId, MOD_EMAIL, null));
    }

    @Test
    void completeSucceedsWithRightKeyAndIdAndClaimsBothItems() {
        String key = approveAndCaptureKey();
        found.markReceivedAtDesk(NOW, moderator.getId());

        DeskMatchResponse res = service.complete(matchId, MOD_EMAIL, new CompleteHandoverRequest(key, 1001L));

        assertEquals(MatchStatus.COMPLETED, res.status());
        assertEquals(ItemStatus.CLAIMED, lost.getStatus());
        assertEquals(ItemStatus.CLAIMED, found.getStatus());
        assertNull(match.getHandoverKeyHash(), "key is one-time");
        verify(matches).supersede(any(), any(), eq(MatchStatus.PENDING), eq(MatchStatus.EXPIRED), eq(NOW));
    }

    @Test
    void completeRequiresTheFoundItemToBeAtTheDesk() {
        String key = approveAndCaptureKey();
        assertThrows(InvalidMatchStateException.class,
                () -> service.complete(matchId, MOD_EMAIL, new CompleteHandoverRequest(key, 1001L)));
        assertEquals(MatchStatus.APPROVED, match.getStatus());
    }

    @Test
    void wrongKeysAreCountedThenLockedUntilReissued() {
        String key = approveAndCaptureKey();
        found.markReceivedAtDesk(NOW, moderator.getId());
        CompleteHandoverRequest wrong = new CompleteHandoverRequest("AAAA-AAAA", 1001L);

        for (int i = 1; i <= 3; i++) {
            assertThrows(InvalidHandoverKeyException.class, () -> service.complete(matchId, MOD_EMAIL, wrong));
            assertEquals(i, match.getHandoverFailedAttempts());
        }
        InvalidHandoverKeyException locked = assertThrows(InvalidHandoverKeyException.class,
                () -> service.complete(matchId, MOD_EMAIL, new CompleteHandoverRequest(key, 1001L)));
        assertTrue(locked.getMessage().toLowerCase().contains("re-issue"));

        service.reissueKey(matchId, MOD_EMAIL);
        ArgumentCaptor<MatchApprovedEvent> cap = ArgumentCaptor.forClass(MatchApprovedEvent.class);
        verify(events, org.mockito.Mockito.times(2)).publishEvent(cap.capture());
        MatchApprovedEvent reissued = cap.getAllValues().get(1);
        assertTrue(reissued.reissued());
        assertEquals(0, match.getHandoverFailedAttempts());

        assertEquals(MatchStatus.COMPLETED, service
                .complete(matchId, MOD_EMAIL, new CompleteHandoverRequest(reissued.handoverKey(), 1001L)).status());
    }

    @Test
    void wrongIdentityIsRejectedWithoutConsumingAnAttempt() {
        String key = approveAndCaptureKey();
        found.markReceivedAtDesk(NOW, moderator.getId());

        assertThrows(InvalidHandoverKeyException.class,
                () -> service.complete(matchId, MOD_EMAIL, new CompleteHandoverRequest(key, 1002L)));
        assertEquals(0, match.getHandoverFailedAttempts());
        assertEquals(MatchStatus.APPROVED, match.getStatus());
    }

    @Test
    void expiredKeyIsRejected() {
        match.approve(moderator.getId(), null, keys.hash("ABCD-EFGH"), NOW, NOW.minusSeconds(5));
        found.markReceivedAtDesk(NOW, moderator.getId());
        InvalidHandoverKeyException e = assertThrows(InvalidHandoverKeyException.class,
                () -> service.complete(matchId, MOD_EMAIL, new CompleteHandoverRequest("ABCD-EFGH", 1001L)));
        assertTrue(e.getMessage().toLowerCase().contains("expired"));
    }

    @Test
    void markReceivedIsIdempotentAndRefusesClosedItems() {
        service.markReceived(found.getId(), MOD_EMAIL);
        // second call must not overwrite the first timestamp
        Instant first = found.getDeskReceivedAt();
        service.markReceived(found.getId(), MOD_EMAIL);
        assertEquals(first, found.getDeskReceivedAt());

        found.close();
        assertThrows(InvalidMatchStateException.class, () -> service.markReceived(found.getId(), MOD_EMAIL));
    }
}
