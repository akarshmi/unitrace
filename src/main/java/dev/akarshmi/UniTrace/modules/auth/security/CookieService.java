package dev.akarshmi.UniTrace.modules.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

@Service
@Getter
public class CookieService {

    private final String refreshTokenCookieName;
    private final boolean cookieHttpOnly;
    private final boolean cookieSecure;
    private final String cookieDomain;
    private final String cookiePath;
    private final String cookieSameSite;

    public CookieService(
            @Value("${unitrace.jwt.cookie-name}") String refreshTokenCookieName,
            @Value("${unitrace.jwt.cookie-http-only}") boolean cookieHttpOnly,
            @Value("${unitrace.jwt.cookie-secure}") boolean cookieSecure,
            @Value("${unitrace.jwt.cookie-domain}") String cookieDomain,
            @Value("${unitrace.jwt.cookie-path}") String cookiePath,
            @Value("${unitrace.jwt.cookie-same-site}") String cookieSameSite) {
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.cookieHttpOnly = cookieHttpOnly;
        this.cookieSecure = cookieSecure;
        this.cookieDomain = cookieDomain;
        this.cookiePath = cookiePath;
        this.cookieSameSite = cookieSameSite;
    }

    public void attachRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenCookieName, value)
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure)
                .domain(cookieDomain)
                .path(cookiePath)
                .sameSite(cookieSameSite)
                .maxAge(maxAgeSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(refreshTokenCookieName, "")
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure)
                .domain(cookieDomain)
                .path(cookiePath)
                .sameSite(cookieSameSite)
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String extractRefreshToken(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, refreshTokenCookieName);
        return cookie != null ? cookie.getValue() : null;
    }

    public void addNoStoreHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}