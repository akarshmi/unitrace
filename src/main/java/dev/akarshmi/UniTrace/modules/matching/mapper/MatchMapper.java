package dev.akarshmi.UniTrace.modules.matching.mapper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.matching.dto.DeskItemView;
import dev.akarshmi.UniTrace.modules.matching.dto.DeskMatchResponse;
import dev.akarshmi.UniTrace.modules.matching.dto.MatchItemView;
import dev.akarshmi.UniTrace.modules.matching.dto.MatchResponse;
import dev.akarshmi.UniTrace.modules.matching.entity.Match;
import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;
import dev.akarshmi.UniTrace.modules.matching.service.DropOffPolicy;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MatchMapper {

    private final DropOffPolicy dropOff;

    public DeskMatchResponse toDesk(Match m) {
        return new DeskMatchResponse(m.getId(), m.getStatus(), m.getFinalScore(), m.getTextScore(),
                m.isImageCompared(), m.getImageScore(), m.getTimeScore(),
                deskView(m.getLostItem()), deskView(m.getFoundItem()),
                m.getCreatedAt(), m.getReviewedAt(), m.getReviewNote(),
                m.getHandoverKeyExpiresAt(), m.getHandoverFailedAttempts(), m.getCompletedAt());
    }

    public MatchResponse toStudent(Match m, UUID viewerId) {
        boolean isLostOwner = m.getLostItem().getReportedBy().getId().equals(viewerId);
        Item other = isLostOwner ? m.getFoundItem() : m.getLostItem();
        return new MatchResponse(m.getId(), m.getStatus(),
                isLostOwner ? "LOST_OWNER" : "FOUND_OWNER",
                (int) Math.round(m.getFinalScore() * 100),
                studentView(other),
                isLostOwner && m.getStatus() == MatchStatus.APPROVED ? m.getHandoverKeyExpiresAt() : null,
                nextStep(m, isLostOwner),
                m.getCreatedAt());
    }

    private String nextStep(Match m, boolean isLostOwner) {
        if (m.getStatus() == MatchStatus.COMPLETED) {
            return "Handover completed.";
        }
        if (m.getStatus() != MatchStatus.APPROVED) {
            return "";
        }
        Item found = m.getFoundItem();
        if (isLostOwner) {
            return "Visit the Security Office with your university ID and the handover key e-mailed to you.";
        }
        return found.getDeskReceivedAt() == null
                ? "Please hand the item in at the Security Office before the drop-off deadline."
                : "The Security Office has your item. Thank you!";
    }

    private MatchItemView studentView(Item i) {
        return new MatchItemView(i.getId(), i.getType(), i.getTitle(), i.getDescription(), i.getLocation(),
                i.getImageUrl(), i.getStatus(), i.getEventFrom(), i.getEventTo(),
                i.getDeskReceivedAt() != null, dropOff.deadlineFor(i.getCreatedAt()));
    }

    private DeskItemView deskView(Item i) {
        User r = i.getReportedBy();
        return new DeskItemView(i.getId(), i.getType(), i.getTitle(), i.getDescription(), i.getLocation(),
                i.getImageUrl(), i.getStatus(), i.getEventFrom(), i.getEventTo(), i.getCreatedAt(),
                dropOff.deadlineFor(i.getCreatedAt()), i.getDeskReceivedAt(),
                r.getFullName(), r.getRegistrationNumber(), r.getUniEmail());
    }
}
