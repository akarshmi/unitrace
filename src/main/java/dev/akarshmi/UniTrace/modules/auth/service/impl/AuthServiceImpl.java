package dev.akarshmi.UniTrace.modules.auth.service.impl;

import dev.akarshmi.UniTrace.modules.auth.dto.*;
import dev.akarshmi.UniTrace.modules.auth.entity.RefreshToken;
import dev.akarshmi.UniTrace.modules.auth.mapper.PendingRegistrationMapper;
import dev.akarshmi.UniTrace.modules.auth.repository.RefreshTokenRepository;
import dev.akarshmi.UniTrace.modules.auth.security.CookieService;
import dev.akarshmi.UniTrace.modules.auth.security.JwtService;
import dev.akarshmi.UniTrace.modules.auth.service.AuthService;
import dev.akarshmi.UniTrace.modules.auth.service.PendingRegistrationService;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.*;
import dev.akarshmi.UniTrace.modules.user.entity.User;
import dev.akarshmi.UniTrace.modules.user.repository.UserRepository;
import dev.akarshmi.UniTrace.modules.utils.services.OtpService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final CookieService cookieService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PendingRegistrationService pendingRegistrationService;
    private final PendingRegistrationMapper  pendingRegistrationMapper;

    private final AuthenticationManager authenticationManager;


    @Value("${unitrace.otp.ttl-minutes}")
    private long otpTtlMinutes;

    @Value("${unitrace.jwt.access-token-TTL}")
    private long accessTokenTtl;

    @Value("${unitrace.jwt.refresh-token-TTL}")
    private long refreshTokenTtl;

    // Registering

    @Override
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsUserByUniEmailOrRegistrationNumber(request.uniEmail(), request.registrationNumber())){
            throw new UserAlreadyExists("User already exists");
        }

        boolean reserved = pendingRegistrationService.reserveRegistrationNumber(request.registrationNumber(), request.uniEmail());

        if (!reserved){
            throw new RegistrationInProgressException("This registration number is already pending verification by another session.");
        }

        PendingRegistration base = PendingRegistration.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .personalEmail(request.personalEmail())
                .registrationNumber(request.registrationNumber())
                .uniEmail(request.uniEmail())
                .phoneNumber(request.phoneNumber())
                .hashedPassword(passwordEncoder.encode(request.password()))
                .department(request.department())
                .build();

        String token = pendingRegistrationService.save(base);
        otpService.generateOtpAndSendEmail(request.uniEmail());

        return new RegisterResponse(request.uniEmail(),token,otpTtlMinutes);
    }

    @Override
    public RegistrationVerifyResponse verifyRegistration(VerifyRegistrationRequest request) {
        otpService.isValid(request.uniEmail(),request.otp());

        PendingRegistration pending = pendingRegistrationService.get(request.uniEmail());

        if (!pending.token().equals(request.token())){
            throw new InvalidRegistrationTokenException("Invalid registration session.");
        }

        User user = pendingRegistrationMapper.toUser(pending);

        try{
            userRepository.save(user);
        }catch (DataIntegrityViolationException e){
            throw new UserAlreadyExists("User already exists");
        }

        pendingRegistrationService.delete(request.uniEmail());
        pendingRegistrationService.releaseRegistrationNumber(pending.registrationNumber());
        return new RegistrationVerifyResponse(pending.uniEmail(),"Account created. Please log in.");
    }

    @Override
    public TokenResponse login(LoginRequest request, HttpServletResponse response) {
        Authentication authentication = authenticate(request);
        User user = userRepository.findByUniEmail(request.uniEmail()).orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        //creating Rfresh token for attachinh in cookie
        String jti =  UUID.randomUUID().toString();

        RefreshToken refreshTokenObj = RefreshToken.builder()
                .jti(jti)
                .user(user)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(refreshTokenTtl))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenObj);


        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user, refreshTokenObj.getJti());


        //attach the refreshCookie after logging in -
        cookieService.attachRefreshCookie(response,refreshToken,refreshTokenTtl);
        cookieService.addNoStoreHeaders(response);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtl)
                .user(new UserSummary(
                        user.getId(),
                        user.getUniEmail(),
                        user.getRole().name()
                ))
                .timestamp(Instant.now().toString())
                .build();
    }

    @Override
    @Transactional
    public TokenResponse refresh( HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenJwt = cookieService.extractRefreshToken(request);
        if (refreshTokenJwt == null){
            throw new RefreshTokenNotFoundException();
        }

        if (!jwtService.isValid(refreshTokenJwt)) {
            throw new InvalidRefreshTokenException();
        }

        //we had found the refreshToken
        String jti = jwtService.getJTI(refreshTokenJwt);
        RefreshToken existing = refreshTokenRepository.findByJti(jti).orElseThrow(RefreshTokenNotFoundException::new);

        if (existing.isRevoked()){
            refreshTokenRepository.deleteByUserId(existing.getUser().getId());
            throw new RefreshTokenRevokedException();
        }
        if (existing.isExpired()) {
            throw new RefreshTokenExpiredException();
        }

        User user = existing.getUser();
        String newJti = UUID.randomUUID().toString();
        existing.revokeAndReplace(newJti);
        existing.markUsed();
        refreshTokenRepository.save(existing);

        RefreshToken newToken = RefreshToken.builder()
                .jti(newJti)
                .user(user)
                .expiresAt(Instant.now().plusSeconds(refreshTokenTtl))
                .build();
        refreshTokenRepository.save(newToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user, newToken.getJti());

        // now attach the refresh token in cookie
        cookieService.attachRefreshCookie(response,newRefreshToken,refreshTokenTtl);
        cookieService.addNoStoreHeaders(response);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtl)
                .user(new UserSummary(user.getId(),user.getUniEmail(),user.getRole().name()))
                .timestamp(Instant.now().toString())
                .build();

    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshTokenJwt = cookieService.extractRefreshToken(request);

            if (refreshTokenJwt != null
                    && !refreshTokenJwt.isBlank()
                    && jwtService.isValid(refreshTokenJwt)
                    && jwtService.isRefreshToken(refreshTokenJwt)) {

                String jti = jwtService.getJTI(refreshTokenJwt);

                refreshTokenRepository.findByJti(jti)
                        .ifPresent(token -> {
                            if (!token.isRevoked()) {
                                token.revoke();
                                refreshTokenRepository.save(token);
                            }
                        });
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid refresh token during logout");
        }
        cookieService.clearRefreshCookie(response);
        cookieService.addNoStoreHeaders(response);
        SecurityContextHolder.clearContext();
    }


    private Authentication authenticate(LoginRequest request) {
        try{
            return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.uniEmail(), request.password()));
        } catch (AuthenticationException e) {
            log.debug("Authentication failed for user: {}", request.uniEmail());
            throw new BadCredentialsException("Invalid username or password.");
        }
    }

}
