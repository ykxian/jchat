package com.jchat.llm;

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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProviderControllerTest {

    private MockMvc mockMvc;
    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        providerService = Mockito.mock(ProviderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProviderController(providerService))
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
    void listReturnsProviderInventory() throws Exception {
        when(providerService.list(7L)).thenReturn(new ProviderListResponse(List.of(
                new ProviderInfo(
                        "openai",
                        "OpenAI Compatible",
                        true,
                        List.of(new ModelSpec("gpt-4o-mini", "GPT-4o mini", 128000, false)),
                        true,
                        List.of(new ProviderKeySummary("1", "Personal"))
                )
        )));

        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("openai"))
                .andExpect(jsonPath("$.items[0].userKeys[0].id").value("1"));
    }
}
