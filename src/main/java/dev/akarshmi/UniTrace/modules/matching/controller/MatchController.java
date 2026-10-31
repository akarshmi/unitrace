package dev.akarshmi.UniTrace.modules.matching.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.akarshmi.UniTrace.modules.matching.dto.MatchResponse;
import dev.akarshmi.UniTrace.modules.matching.service.MatchQueryService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/uni/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchQueryService matchQueryService;

    /** Desk-approved matches for the logged-in student (either side of the match). */
    @GetMapping("/mine")
    public Page<MatchResponse> mine(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        return matchQueryService.myMatches(authentication.getName(), pageable);
    }
}
