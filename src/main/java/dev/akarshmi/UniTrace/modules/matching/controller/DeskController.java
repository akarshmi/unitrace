package dev.akarshmi.UniTrace.modules.matching.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.matching.dto.CompleteHandoverRequest;
import dev.akarshmi.UniTrace.modules.matching.dto.DeskMatchResponse;
import dev.akarshmi.UniTrace.modules.matching.dto.ModerationNoteRequest;
import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;
import dev.akarshmi.UniTrace.modules.matching.service.MatchModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Lost &amp; Found Desk API. Restricted to MODERATOR / ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/uni/v1/desk")
@RequiredArgsConstructor
public class DeskController {

    private final MatchModerationService moderation;

    @GetMapping("/matches")
    public Page<DeskMatchResponse> list(
            @RequestParam(defaultValue = "PENDING") MatchStatus status,
            @PageableDefault(size = 20, sort = "finalScore", direction = Sort.Direction.DESC) Pageable pageable) {
        return moderation.list(status, pageable);
    }

    @GetMapping("/matches/{id}")
    public DeskMatchResponse get(@PathVariable UUID id) {
        return moderation.get(id);
    }

    @PostMapping("/matches/{id}/approve")
    public DeskMatchResponse approve(@PathVariable UUID id, Authentication auth,
                                     @Valid @RequestBody(required = false) ModerationNoteRequest body) {
        return moderation.approve(id, auth.getName(), note(body));
    }

    @PostMapping("/matches/{id}/reject")
    public DeskMatchResponse reject(@PathVariable UUID id, Authentication auth,
                                    @Valid @RequestBody(required = false) ModerationNoteRequest body) {
        return moderation.reject(id, auth.getName(), note(body));
    }

    @PostMapping("/matches/{id}/reissue-key")
    public DeskMatchResponse reissueKey(@PathVariable UUID id, Authentication auth) {
        return moderation.reissueKey(id, auth.getName());
    }

    @PostMapping("/matches/{id}/complete")
    public DeskMatchResponse complete(@PathVariable UUID id, Authentication auth,
                                      @Valid @RequestBody CompleteHandoverRequest body) {
        return moderation.complete(id, auth.getName(), body);
    }

    /** Desk logs that the physical item was handed in. */
    @PostMapping("/items/{id}/receive")
    public ItemResponse receive(@PathVariable UUID id, Authentication auth) {
        return moderation.markReceived(id, auth.getName());
    }

    private static String note(ModerationNoteRequest body) {
        return body == null ? null : body.note();
    }
}
