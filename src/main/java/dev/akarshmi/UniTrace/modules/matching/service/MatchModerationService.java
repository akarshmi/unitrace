package dev.akarshmi.UniTrace.modules.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidHandoverKeyException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.InvalidMatchStateException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.ItemNotFoundException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.MatchNotFoundException;
import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
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

/**
 * Lost &amp; Found Desk workflow. AI matches wait as PENDING; a moderator approves (students are then notified and the
 * lost-item owner gets a one-time handover key), rejects, or completes the physical handover after checking ID.
 */
@Service
public class MatchModerationService {

    private final MatchRepository matches;
    private final ItemRepository items;
    private final UserRepository users;
    private final HandoverKeyService keys;
    private final MatchMapper mapper;
    private final ApplicationEventPublisher events;
    private final DeskProperties desk;
    private final Clock clock;

    @Autowired
    public MatchModerationService(MatchRepository matches, ItemRepository items, UserRepository users,
                                  HandoverKeyService keys, MatchMapper mapper, ApplicationEventPublisher events,
                                  DeskProperties desk) {
        this(matches, items, users, keys, mapper, events, desk, Clock.systemUTC());
    }

    MatchModerationService(MatchRepository matches, ItemRepository items, UserRepository users,
                           HandoverKeyService keys, MatchMapper mapper, ApplicationEventPublisher events,
                           DeskProperties desk, Clock clock) {
        this.matches = matches;
        this.items = items;
        this.users = users;
        this.keys = keys;
        this.mapper = mapper;
        this.events = events;
        this.desk = desk;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DeskMatchResponse> list(MatchStatus status, Pageable pageable) {
        return matches.findByStatus(status, pageable).map(mapper::toDesk);
    }

    @Transactional(readOnly = true)
    public DeskMatchResponse get(UUID matchId) {
        return mapper.toDesk(load(matchId));
    }

    @Transactional
    public DeskMatchResponse approve(UUID matchId, String moderatorEmail, String note) {
        User moderator = moderator(moderatorEmail);
        Match m = load(matchId);
        m.requireStatus(MatchStatus.PENDING);
        Item lost = m.getLostItem();
        Item found = m.getFoundItem();
        if (lost.getStatus() != ItemStatus.OPEN || found.getStatus() != ItemStatus.OPEN) {
            throw new InvalidMatchStateException("One of the items is no longer open; this match cannot be approved.");
        }

        Instant now = clock.instant();
        Instant expires = now.plus(desk.keyTtl());
        String key = keys.generate();
        m.approve(moderator.getId(), note, keys.hash(key), now, expires);
        lost.markMatched();
        found.markMatched();

        events.publishEvent(new MatchApprovedEvent(m.getId(), lost.getReportedBy().getUniEmail(),
                found.getReportedBy().getUniEmail(), lost.getTitle(), found.getTitle(), key, expires, false));
        return mapper.toDesk(m);
    }

    @Transactional
    public DeskMatchResponse reject(UUID matchId, String moderatorEmail, String note) {
        User moderator = moderator(moderatorEmail);
        Match m = load(matchId);
        m.reject(moderator.getId(), note, clock.instant());
        return mapper.toDesk(m);
    }

    /** For a student who lost the e-mail, or after the key got locked / expired. */
    @Transactional
    public DeskMatchResponse reissueKey(UUID matchId, String moderatorEmail) {
        moderator(moderatorEmail);
        Match m = load(matchId);
        m.requireStatus(MatchStatus.APPROVED);

        Instant expires = clock.instant().plus(desk.keyTtl());
        String key = keys.generate();
        m.reissueKey(keys.hash(key), expires);

        events.publishEvent(new MatchApprovedEvent(m.getId(), m.getLostItem().getReportedBy().getUniEmail(),
                m.getFoundItem().getReportedBy().getUniEmail(), m.getLostItem().getTitle(),
                m.getFoundItem().getTitle(), key, expires, true));
        return mapper.toDesk(m);
    }

    /** Wrong-key attempts must be persisted, so this exception type does not roll the transaction back. */
    @Transactional(noRollbackFor = InvalidHandoverKeyException.class)
    public DeskMatchResponse complete(UUID matchId, String moderatorEmail, CompleteHandoverRequest req) {
        User moderator = moderator(moderatorEmail);
        Match m = load(matchId);
        m.requireStatus(MatchStatus.APPROVED);
        Item lost = m.getLostItem();
        Item found = m.getFoundItem();
        Instant now = clock.instant();

        if (found.getDeskReceivedAt() == null) {
            throw new InvalidMatchStateException("The found item has not been received at the desk yet.");
        }
        if (m.isKeyLocked(desk.maxKeyAttempts())) {
            throw new InvalidHandoverKeyException("Too many wrong keys. Re-issue the key to continue.");
        }
        if (m.getHandoverKeyExpiresAt() == null || m.getHandoverKeyExpiresAt().isBefore(now)) {
            throw new InvalidHandoverKeyException("The handover key has expired. Re-issue it.");
        }
        Long expectedReg = lost.getReportedBy().getRegistrationNumber();
        if (!expectedReg.equals(req.verifiedRegistrationNumber())) {
            throw new InvalidHandoverKeyException(
                    "The ID shown does not belong to the person who reported the lost item.");
        }
        if (!keys.matches(req.handoverKey(), m.getHandoverKeyHash())) {
            m.recordFailedKeyAttempt();
            throw new InvalidHandoverKeyException("Handover key is incorrect.");
        }

        m.complete(moderator.getId(), now);
        lost.markClaimed();
        found.markClaimed();
        matches.supersede(m.getId(), List.of(lost.getId(), found.getId()),
                MatchStatus.PENDING, MatchStatus.EXPIRED, now);
        return mapper.toDesk(m);
    }

    /** The desk logs that the physical item was handed in. */
    @Transactional
    public ItemResponse markReceived(UUID itemId, String moderatorEmail) {
        User moderator = moderator(moderatorEmail);
        Item item = items.findById(itemId).orElseThrow(ItemNotFoundException::new);
        if (item.getStatus() == ItemStatus.CLOSED || item.getStatus() == ItemStatus.CLAIMED) {
            throw new InvalidMatchStateException("Item is " + item.getStatus() + " and cannot be received.");
        }
        if (item.getDeskReceivedAt() == null) {
            item.markReceivedAtDesk(clock.instant(), moderator.getId());
        }
        return ItemResponse.from(item);
    }

    private Match load(UUID id) {
        return matches.fetchById(id).orElseThrow(MatchNotFoundException::new);
    }

    private User moderator(String email) {
        return users.findByUniEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }
}
