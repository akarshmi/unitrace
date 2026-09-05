package dev.akarshmi.UniTrace.modules.auth.security;

import dev.akarshmi.UniTrace.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey jwtSecret;
    private final long accessTokenTTL;
    private final long refreshTokenTTL;
    private final String issuer;
    private final String refreshTokenCookieName;


    public JwtService(
            @Value("${unitrace.jwt.secret}")
            String jwtSecret,

            @Value("${unitrace.jwt.access-token-TTL}")
            long accessTokenTTL,

            @Value("${unitrace.jwt.refresh-token-TTL}")
            long refreshTokenTTL,

            @Value("${unitrace.jwt.issuer}")
            String issuer,

            @Value("${unitrace.jwt.refresh-token-cookie-name}")
            String refreshTokenCookieName)
    {
        this.jwtSecret = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTTL = accessTokenTTL;
        this.refreshTokenTTL = refreshTokenTTL;
        this.issuer = issuer;
        this.refreshTokenCookieName = refreshTokenCookieName;
    }

    //generate accessToken
    public String generateAccessToken(User user) {
        String roles = user.getRole() == null ?
                null : user.getRole().name();
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTTL)))
                .claims(Map.of(
                        "email", user.getUniEmail(),
                        "roles", roles,
                        "typ","access"
                ))
                .signWith(jwtSecret)
                .compact();
    }

    //generate refreshToken
    public String generateRefreshToken(User user, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(jti)
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTokenTTL)))
                .claim(
                        "typ","refresh"
                )
                .signWith(jwtSecret)
                .compact();
    }

    //token parser
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .requireIssuer(issuer)
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token);
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }


    public boolean isAccessToken(String token){
        Claims c = parse(token).getPayload();
        return "access".equals(c.get("typ"));
    }

    public boolean isRefreshToken(String token){
        Claims c = parse(token).getPayload();
        return "refresh".equals(c.get("typ"));
    }

    public UUID getUserId(String token){
        Claims c = parse(token).getPayload();
        return UUID.fromString(c.getSubject());
    }

    public String getJTI(String token){
        return parse(token).getPayload().getId();
    }



}
