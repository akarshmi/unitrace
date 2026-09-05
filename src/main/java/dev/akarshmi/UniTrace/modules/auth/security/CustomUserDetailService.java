package dev.akarshmi.UniTrace.modules.auth.security;

import dev.akarshmi.UniTrace.modules.user.entity.User;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Getter
@Setter
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        User user = userRepository.findByUniEmail(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid username or password")
                );

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUniEmail())
                .password(user.getHashedPassword())
                .roles(user.getRole().name())
                .build();
    }
}
