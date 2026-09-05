package dev.akarshmi.UniTrace.modules.auth.security;

import dev.akarshmi.UniTrace.modules.configs.PublicEndpoints;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import io.jsonwebtoken.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") ) {

            //extract the token from the header-
            String token = header.substring(7);

            if (!jwtService.isAccessToken(token)) {
                filterChain.doFilter(request,response);
                return;
            }

            //then authentication and set the authentication in the security context holder
            try{
                Jws<Claims> parsed = jwtService.parse(token);
                Claims payload = parsed.getPayload();

                UUID userID = UUID.fromString(payload.getSubject());


                //find user in db

                userRepository.findById(userID).ifPresent(user -> {

                    //we have hot the user
                    List<GrantedAuthority> authorities =
                            user.getRole() == null
                            ? List.of()
                            : List.of(
                                    new SimpleGrantedAuthority(
                                            "ROLE_" + user.getRole().name()
                                    )
                            );

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user.getUniEmail(),
                                    null,
                                    authorities
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    //final- set the context to the security context
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                });




            } catch (MalformedJwtException e) {
                log.error("Malformed JWT provided", e);
            } catch (ExpiredJwtException e) {
                log.error("Expired JWT provided", e);
            } catch (InvalidClaimException e) {
                log.error("Invalid JWT claim", e);
            } catch (JwtException e) {
                log.error("JWT authentication failed", e);
            } catch (Exception e) {
                log.error("Unexpected error during JWT authentication", e);
            }
        }
        filterChain.doFilter(request,response);
//        doFilter(request, response, filterChain);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicEndpoints.URLS.contains(request.getServletPath());
    }
}
