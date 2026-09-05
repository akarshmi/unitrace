package dev.akarshmi.UniTrace.modules.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.akarshmi.UniTrace.modules.auth.dto.ErrorResponse;
import dev.akarshmi.UniTrace.modules.auth.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authRequest -> authRequest
                        .requestMatchers(
                                "/api/uni/v1/auth/registration/initiation",
                                "/api/uni/v1/auth/registration/verification",
                                "/api/uni/v1/auth/login",
                                "/api/uni/v1/auth/refresh"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exHandler -> exHandler.authenticationEntryPoint((request, response, authException) -> {
                    log.error("Authentication failed", authException);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    ErrorResponse errorResponse = new ErrorResponse(
                            "Unauthorized Access",
                            "error",
                            HttpServletResponse.SC_UNAUTHORIZED,
                            Instant.now().toString(),
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getWriter(), errorResponse);
                })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
                //add jwt filter
        return http.build();
    }
}
