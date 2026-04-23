package com.jchat.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.auth.dto.LoginRequest;
import com.jchat.auth.dto.RegisterRequest;
import com.jchat.auth.entity.User;
import com.jchat.auth.security.CookieUtils;
import com.jchat.auth.service.AuthService;
import com.jchat.auth.service.LoginResult;
import com.jchat.common.api.GlobalExceptionHandler;
import com.jchat.common.web.CorrelationIdFilter;
import com.jchat.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        authService = Mockito.mock(AuthService.class);
        objectMapper = new ObjectMapper();

        AppProperties appProperties = new AppProperties();
        appProperties.getAuth().setRefreshTokenTtl(Duration.ofDays(7));
        appProperties.getAuth().setRefreshCookieName("refresh");
        appProperties.getAuth().setRefreshCookieSameSite("Lax");
        appProperties.getAuth().setRefreshCookieSecure(false);

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, new CookieUtils(appProperties)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void registerReturnsCreatedUser() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setCreatedAt(Instant.parse("2026-04-23T10:15:30Z"));

        when(authService.register(any(RegisterRequest.class), anyString())).thenReturn(user);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("alice@example.com", "Passw0rd!", "Alice"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-23T10:15:30Z"));
    }

    @Test
    void loginReturnsAccessTokenAndRefreshCookie() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setCreatedAt(Instant.parse("2026-04-23T10:15:30Z"));

        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(
                new LoginResult(user, "jwt-token", "refresh-token", 900)
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("alice@example.com", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refresh=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void refreshWithoutCookieReturnsUnauthorizedError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    @Test
    void logoutClearsRefreshCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }
}
