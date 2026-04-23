package com.jchat.auth.service;

import com.jchat.auth.dto.LoginRequest;
import com.jchat.auth.dto.RegisterRequest;
import com.jchat.auth.entity.RefreshToken;
import com.jchat.auth.entity.User;
import com.jchat.auth.jwt.JwtService;
import com.jchat.auth.repository.RefreshTokenRepository;
import com.jchat.auth.repository.UserRepository;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AuthService {

    private static final HexFormat HEX_FORMAT = HexFormat.of();
    private static final char[] TOKEN_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties.Auth authProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authProperties = appProperties.getAuth();
    }

    public User register(RegisterRequest request, String ipAddress) {
        String normalizedEmail = normalizeEmail(request.email());
        validatePassword(request.password());

        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new ApiException(ErrorCode.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(resolveDisplayName(request.displayName(), normalizedEmail));
        user.setEmailVerified(false);
        user.setActive(true);
        return userRepository.save(user);
    }

    public LoginResult login(LoginRequest request, String userAgent, String ipAddress) {
        String normalizedEmail = normalizeEmail(request.email());
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Invalid credentials"));

        if (!user.isActive()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.AUTH_INVALID, "Invalid credentials");
        }

        return issueTokens(user, userAgent, ipAddress);
    }

    public LoginResult refresh(String refreshToken, String userAgent, String ipAddress) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Invalid refresh token"));

        Instant now = Instant.now();
        if (storedToken.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllByUserId(storedToken.getUserId(), now);
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token reuse detected");
        }

        if (storedToken.getExpiresAt().isBefore(now)) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh token expired");
        }

        storedToken.setRevokedAt(now);

        User user = userRepository.findByIdAndDeletedAtIsNull(storedToken.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Authenticated user not found"));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Account is deactivated");
        }

        return issueTokens(user, userAgent, ipAddress);
    }

    public void logout(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    private LoginResult issueTokens(User user, String userAgent, String ipAddress) {
        String accessToken = jwtService.issueAccessToken(user);
        String plainRefreshToken = generateRefreshToken();

        RefreshToken token = new RefreshToken();
        token.setUserId(user.getId());
        token.setTokenHash(hashToken(plainRefreshToken));
        token.setExpiresAt(Instant.now().plus(authProperties.getRefreshTokenTtl()));
        token.setUserAgent(truncate(userAgent, 500));
        token.setIp(truncate(ipAddress, 255));
        refreshTokenRepository.save(token);

        return new LoginResult(
                user,
                accessToken,
                plainRefreshToken,
                jwtService.getAccessTokenExpiresInSeconds()
        );
    }

    private String normalizeEmail(String email) {
        return Objects.requireNonNull(email, "email").trim().toLowerCase();
    }

    private String resolveDisplayName(String displayName, String normalizedEmail) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        int separator = normalizedEmail.indexOf('@');
        return separator > 0 ? normalizedEmail.substring(0, separator) : normalizedEmail;
    }

    private void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password must be at least 8 characters");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Password must contain letters and digits");
        }
    }

    private String generateRefreshToken() {
        StringBuilder builder = new StringBuilder(43);
        for (int i = 0; i < 43; i++) {
            builder.append(TOKEN_ALPHABET[ThreadLocalRandom.current().nextInt(TOKEN_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX_FORMAT.formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
