package com.jchat.auth.security;

import com.jchat.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtils {

    private final AppProperties.Auth authProperties;

    public CookieUtils(AppProperties appProperties) {
        this.authProperties = appProperties.getAuth();
    }

    public ResponseCookie createRefreshCookie(String value) {
        return ResponseCookie.from(authProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite(authProperties.getRefreshCookieSameSite())
                .path("/")
                .maxAge(authProperties.getRefreshTokenTtl())
                .build();
    }

    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(authProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(authProperties.isRefreshCookieSecure())
                .sameSite(authProperties.getRefreshCookieSameSite())
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    public String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> authProperties.getRefreshCookieName().equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}
