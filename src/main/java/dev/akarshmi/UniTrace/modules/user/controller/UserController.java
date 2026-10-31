package dev.akarshmi.UniTrace.modules.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.akarshmi.UniTrace.modules.item.dto.ItemResponse;
import dev.akarshmi.UniTrace.modules.user.dto.UpdateProfileRequest;
import dev.akarshmi.UniTrace.modules.user.dto.UserSummary;
import dev.akarshmi.UniTrace.modules.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/uni/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserSummary me(Authentication authentication) {
        return userService.me(authentication.getName());
    }

    @PatchMapping("/me")
    public UserSummary updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    /** "My reports": items reported by the logged-in user, newest first. */
    @GetMapping("/me/items")
    public Page<ItemResponse> myItems(Authentication authentication,
                                      @PageableDefault(size = 20, sort = "createdAt",
                                              direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return userService.myItems(authentication.getName(), pageable);
    }
}
