package com.jchat.apikey.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.apikey.dto.ApiKeyListResponse;
import com.jchat.apikey.dto.ApiKeyResponse;
import com.jchat.apikey.dto.CreateApiKeyRequest;
import com.jchat.apikey.service.ApiKeyService;
import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.api.GlobalExceptionHandler;
import com.jchat.common.web.CorrelationIdFilter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiKeyControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = Mockito.mock(ApiKeyService.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new ApiKeyController(apiKeyService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();

        JwtPrincipal principal = new JwtPrincipal(7L, "alice@example.com", "Alice");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listReturnsStoredKeys() throws Exception {
        when(apiKeyService.list(7L)).thenReturn(new ApiKeyListResponse(List.of(
                new ApiKeyResponse("1", "openai", "Personal", "1234", "2026-04-24T10:15:30Z")
        )));

        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].provider").value("openai"))
                .andExpect(jsonPath("$.items[0].last4").value("1234"));
    }

    @Test
    void createReturnsCreatedKey() throws Exception {
        when(apiKeyService.create(eq(7L), any(CreateApiKeyRequest.class))).thenReturn(
                new ApiKeyResponse("1", "openai", "Personal", "1234", "2026-04-24T10:15:30Z")
        );

        mockMvc.perform(post("/api/v1/api-keys")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateApiKeyRequest("openai", "Personal", "sk-test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/api-keys/1"))
                .andExpect(status().isNoContent());
    }
}
