package dev.akarshmi.UniTrace.modules.matching.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akarshmi.UniTrace.modules.matching.dto.MatchResponse;
import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;
import dev.akarshmi.UniTrace.modules.matching.mapper.MatchMapper;
import dev.akarshmi.UniTrace.modules.matching.repository.MatchRepository;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchQueryService {

    /** Students see their own pending candidates and matches that reached an actionable outcome. */
    private static final List<MatchStatus> VISIBLE = List.of(
            MatchStatus.PENDING, MatchStatus.APPROVED, MatchStatus.COMPLETED);

    private final MatchRepository matches;
    private final UserRepository users;
    private final MatchMapper mapper;

    public Page<MatchResponse> myMatches(String email, Pageable pageable) {
        User user = users.findByUniEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
        return matches.findVisibleForUser(user.getId(), VISIBLE, pageable)
                .map(m -> mapper.toStudent(m, user.getId()));
    }
}
