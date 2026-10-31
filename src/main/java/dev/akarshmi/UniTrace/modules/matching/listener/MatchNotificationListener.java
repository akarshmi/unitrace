package dev.akarshmi.UniTrace.modules.matching.listener;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;
import dev.akarshmi.UniTrace.modules.matching.event.MatchApprovedEvent;
import dev.akarshmi.UniTrace.modules.matching.event.MatchCreatedEvent;
import dev.akarshmi.UniTrace.modules.utils.services.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Mail is sent after commit and asynchronously; failures are logged and never affect the originating request. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchNotificationListener {

    private final EmailSender emailSender;
    private final DeskProperties desk;

    /** AI found a potential match: tell the desk first (students are told only after approval). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchCreated(MatchCreatedEvent e) {
        String deskEmail = desk.deskEmail();
        if (deskEmail == null || deskEmail.isBlank()) {
            log.info("New potential match {} (score {}); unitrace.desk.desk-email not set, no mail sent",
                    e.matchId(), String.format(Locale.ROOT, "%.2f", e.finalScore()));
            return;
        }
        safely(() -> emailSender.sendPlainEmail(deskEmail,
                "UniTrace: new potential match to review",
                """
                A new potential Lost & Found match is waiting for review.

                Confidence: %d%%
                Match id:   %s

                Open the desk portal to approve or reject it.
                """.formatted(Math.round(e.finalScore() * 100), e.matchId())));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMatchApproved(MatchApprovedEvent e) {
        String expires = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                .withZone(desk.zoneId())
                .format(e.keyExpiresAt());

        safely(() -> emailSender.sendPlainEmail(e.lostOwnerEmail(),
                e.reissued() ? "UniTrace: your new handover key" : "UniTrace: we may have found your item",
                """
                Good news: the Lost & Found desk approved a match for your lost item "%s".

                Visit the Security Office with your university ID and give them this one-time key:

                    %s

                The key is valid until %s. Nobody's contact details are shared; the handover happens at the desk.
                """.formatted(e.lostTitle(), e.handoverKey(), expires)));

        if (!e.reissued()) {
            safely(() -> emailSender.sendPlainEmail(e.foundOwnerEmail(),
                    "UniTrace: possible owner found for the item you reported",
                    """
                    The Lost & Found desk found a possible owner for "%s".

                    If you have not done so yet, please hand the item in at the Security Office
                    (by 5 PM on the day you reported it). The owner will collect it there.
                    """.formatted(e.foundTitle())));
        }
    }

    private void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            log.error("Failed to send match notification", ex);
        }
    }
}
