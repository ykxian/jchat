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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setRefreshTokenTtl(Duration.ofDays(7));
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtService,
                appProperties
        );
    }

    @Test
    void registerHashesPasswordAndNormalizesEmail() {
        when(userRepository.existsByEmailAndDeletedAtIsNull("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        User user = authService.register(new RegisterRequest(" Alice@Example.com ", "Passw0rd!", ""), "127.0.0.1");

        verify(userRepository).save(userCaptor.capture());
        assertEquals("alice@example.com", userCaptor.getValue().getEmail());
        assertEquals("hashed-password", userCaptor.getValue().getPasswordHash());
        assertEquals("alice", userCaptor.getValue().getDisplayName());
        assertEquals(1L, user.getId());
    }

    @Test
    void registerRejectsWeakPassword() {
        ApiException exception = assertThrows(ApiException.class,
                () -> authService.register(new RegisterRequest("alice@example.com", "weak", "Alice"), "127.0.0.1"));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setPasswordHash("stored-hash");
        user.setActive(true);

        when(userRepository.findByEmailAndDeletedAtIsNull("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "stored-hash")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "Passw0rd!"), "JUnit", "127.0.0.1"));

        assertEquals(ErrorCode.AUTH_INVALID, exception.getErrorCode());
    }

    @Test
    void refreshRotatesTokenAndIssuesNewCredentials() {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setActive(true);

        RefreshToken storedToken = new RefreshToken();
        storedToken.setId(9L);
        storedToken.setUserId(1L);
        storedToken.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(user)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpiresInSeconds()).thenReturn(900L);

        LoginResult result = authService.refresh("plain-refresh-token", "JUnit", "127.0.0.1");

        assertEquals("new-access-token", result.accessToken());
        assertNotNull(result.refreshToken());
        assertEquals(900L, result.expiresIn());
        assertNotNull(storedToken.getRevokedAt());
        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
        assertEquals(1L, refreshTokenCaptor.getValue().getUserId());
    }

    @Test
    void refreshRejectsReuseAndRevokesAllTokens() {
        RefreshToken storedToken = new RefreshToken();
        storedToken.setUserId(1L);
        storedToken.setRevokedAt(Instant.now().minusSeconds(30));
        storedToken.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));

        ApiException exception = assertThrows(ApiException.class,
                () -> authService.refresh("plain-refresh-token", "JUnit", "127.0.0.1"));

        assertEquals(ErrorCode.AUTH_REFRESH_INVALID, exception.getErrorCode());
        verify(refreshTokenRepository).revokeAllByUserId(org.mockito.ArgumentMatchers.eq(1L), any(Instant.class));
    }
}
