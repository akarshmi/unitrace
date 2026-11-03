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
        Item own = isLostOwner ? m.getLostItem() : m.getFoundItem();
        Item other = isLostOwner ? m.getFoundItem() : m.getLostItem();
        return new MatchResponse(m.getId(), m.getStatus(),
                isLostOwner ? "LOST_OWNER" : "FOUND_OWNER",
                (int) Math.round(m.getFinalScore() * 100),
                m.getFinalScore(), m.getTextScore(), m.isImageCompared(), m.getImageScore(), m.getTimeScore(),
                studentView(own), studentView(other),
                isLostOwner && m.getStatus() == MatchStatus.APPROVED && m.getHandoverKeyHash() != null,
                isLostOwner && m.getStatus() == MatchStatus.APPROVED ? m.getHandoverKeyExpiresAt() : null,
                nextStep(m, isLostOwner),
                m.getCreatedAt(), m.getReviewedAt(), m.getCompletedAt());
    }

    private String nextStep(Match m, boolean isLostOwner) {
        if (m.getStatus() == MatchStatus.COMPLETED) {
            return "Handover completed.";
        }
        if (m.getStatus() != MatchStatus.APPROVED) {
            return "Your match is awaiting Campus Security review.";
        }
        Item found = m.getFoundItem();
        if (isLostOwner) {
            return found.getDeskReceivedAt() == null
                    ? "Your match has been approved. Collection will be available once Campus Security receives the physical item."
                    : "Your item is ready for collection. Visit the Security Office with your university ID. A handover key has been issued; email delivery status is not available here.";
        }
        return found.getDeskReceivedAt() == null
                ? "Please bring the found item to the Campus Security Office. The item must be physically received before collection can be authorized."
                : "Campus Security has received your item. It is now in university custody.";
    }

    private MatchItemView studentView(Item i) {
        return new MatchItemView(i.getId(), i.getType(), i.getTitle(), i.getDescription(), i.getLocation(),
                i.getImageUrl(), i.getStatus(), i.getEventFrom(), i.getEventTo(),
                i.getCreatedAt(), i.getDeskReceivedAt(), dropOff.deadlineFor(i.getCreatedAt()));
    }

    private DeskItemView deskView(Item i) {
        User r = i.getReportedBy();
        return new DeskItemView(i.getId(), i.getType(), i.getTitle(), i.getDescription(), i.getLocation(),
                i.getImageUrl(), i.getStatus(), i.getEventFrom(), i.getEventTo(), i.getCreatedAt(),
                dropOff.deadlineFor(i.getCreatedAt()), i.getDeskReceivedAt(),
                r.getFullName(), r.getRegistrationNumber(), r.getUniEmail());
    }
}
