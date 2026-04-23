package com.jchat.auth.jwt;

import com.jchat.auth.entity.User;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;

    public JwtService(AppProperties appProperties) {
        byte[] secret = Base64.getDecoder().decode(appProperties.getAuth().getJwtSecret());
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.accessTokenTtl = appProperties.getAuth().getAccessTokenTtl();
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("displayName", user.getDisplayName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipal parseAccessToken(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtPrincipal(
                    Long.parseLong(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("displayName", String.class)
            );
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ErrorCode.AUTH_EXPIRED, "Access token expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.AUTH_INVALID, "Invalid access token");
        }
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenTtl.toSeconds();
    }
}
