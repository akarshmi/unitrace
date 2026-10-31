package dev.akarshmi.UniTrace.modules.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.item.repository.ItemQueryRepository;
import dev.akarshmi.UniTrace.modules.user.dto.UpdateProfileRequest;
import dev.akarshmi.UniTrace.modules.user.dto.UserSummary;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ItemQueryRepository itemQueryRepository;

    /** The security principal is the university e-mail (see JwtAuthenticationFilter). */
    public User requireByEmail(String email) {
        return userRepository.findByUniEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));
    }

    @Transactional(readOnly = true)
    public UserSummary me(String email) {
        return UserSummary.from(requireByEmail(email));
    }

    @Transactional
    public UserSummary updateProfile(String email, UpdateProfileRequest req) {
        User user = requireByEmail(email);
        if (req.firstName() != null) user.setFirstName(req.firstName().strip());
        if (req.lastName() != null) user.setLastName(req.lastName().strip());
        if (req.personalEmail() != null) user.setPersonalEmail(req.personalEmail().strip());
        if (req.phoneNumber() != null) user.setPhoneNumber(req.phoneNumber().strip());
        if (req.department() != null) user.setDepartment(req.department().strip());
        return UserSummary.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<ItemResponse> myItems(String email, Pageable pageable) {
        User user = requireByEmail(email);
        return itemQueryRepository.findByReportedBy_Id(user.getId(), pageable).map(ItemResponse::from);
    }
}
