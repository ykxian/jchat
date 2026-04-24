package com.jchat.plugin;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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

class PluginControllerTest {

    private MockMvc mockMvc;
    private PluginService pluginService;

    @BeforeEach
    void setUp() {
        pluginService = Mockito.mock(PluginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PluginController(pluginService))
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
    void listReturnsPluginInventory() throws Exception {
        when(pluginService.list()).thenReturn(new PluginListResponse(List.of(
                new PluginInfo(
                        "calculator",
                        "Calculator",
                        "Evaluate a math expression.",
                        true,
                        null,
                        JsonNodeFactory.instance.objectNode().put("type", "object")
                )
        )));

        mockMvc.perform(get("/api/v1/plugins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("calculator"))
                .andExpect(jsonPath("$.items[0].displayName").value("Calculator"))
                .andExpect(jsonPath("$.items[0].enabled").value(true));
    }
}
