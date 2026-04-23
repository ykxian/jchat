package com.jchat.auth.jwt;

import com.jchat.auth.entity.User;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void issuesAndParsesAccessToken() {
        JwtService jwtService = new JwtService(appProperties(Duration.ofMinutes(15),
                "MWYzMjU0NzY5OGFiY2RlZjAxMjM0NTY3ODlhYmNkZWYxZjMyNTQ3Njk4YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg=="));

        User user = new User();
        user.setId(7L);
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");

        String token = jwtService.issueAccessToken(user);
        JwtPrincipal principal = jwtService.parseAccessToken(token);

        assertEquals(7L, principal.userId());
        assertEquals("alice@example.com", principal.email());
        assertEquals("Alice", principal.displayName());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(appProperties(Duration.ofMinutes(15),
                "MWYzMjU0NzY5OGFiY2RlZjAxMjM0NTY3ODlhYmNkZWYxZjMyNTQ3Njk4YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg=="));
        JwtService parser = new JwtService(appProperties(Duration.ofMinutes(15),
                "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWZhYmNkZWYwMTIzNDU2Nzg5"));

        User user = new User();
        user.setId(7L);
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");

        ApiException exception = assertThrows(ApiException.class, () -> parser.parseAccessToken(issuer.issueAccessToken(user)));
        assertEquals(ErrorCode.AUTH_INVALID, exception.getErrorCode());
    }

    @Test
    void rejectsExpiredToken() {
        JwtService jwtService = new JwtService(appProperties(Duration.ofSeconds(-1),
                "MWYzMjU0NzY5OGFiY2RlZjAxMjM0NTY3ODlhYmNkZWYxZjMyNTQ3Njk4YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg=="));

        User user = new User();
        user.setId(7L);
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");

        ApiException exception = assertThrows(ApiException.class, () -> jwtService.parseAccessToken(jwtService.issueAccessToken(user)));
        assertEquals(ErrorCode.AUTH_EXPIRED, exception.getErrorCode());
    }

    private AppProperties appProperties(Duration accessTokenTtl, String jwtSecret) {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setAccessTokenTtl(accessTokenTtl);
        appProperties.getAuth().setJwtSecret(jwtSecret);
        return appProperties;
    }
}
