package com.jchat.mask.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.api.GlobalExceptionHandler;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.web.CorrelationIdFilter;
import com.jchat.mask.dto.CreateMaskRequest;
import com.jchat.mask.dto.MaskResponse;
import com.jchat.mask.dto.UpdateMaskRequest;
import com.jchat.mask.service.MaskService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaskControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MaskService maskService;

    @BeforeEach
    void setUp() {
        maskService = Mockito.mock(MaskService.class);
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(new MaskController(maskService))
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
    void listReturnsCursorPage() throws Exception {
        when(maskService.list(7L, null, 20, null, false)).thenReturn(new CursorPage<>(
                List.of(new MaskResponse(
                        "1",
                        null,
                        "代码审查员",
                        "🧐",
                        "prompt",
                        "anthropic",
                        "claude-sonnet-4-6",
                        0.3,
                        1.0,
                        null,
                        List.of("code", "review"),
                        true,
                        "2026-04-24T10:00:00Z",
                        "2026-04-24T10:00:00Z"
                )),
                null
        ));

        mockMvc.perform(get("/api/v1/masks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("代码审查员"));
    }

    @Test
    void createReturnsCreatedMask() throws Exception {
        when(maskService.create(eq(7L), any(CreateMaskRequest.class))).thenReturn(new MaskResponse(
                "9",
                "7",
                "Python 导师",
                "🐍",
                "prompt",
                "openai",
                "gpt-4o-mini",
                0.5,
                1.0,
                null,
                List.of("python"),
                false,
                "2026-04-24T10:00:00Z",
                "2026-04-24T10:00:00Z"
        ));

        mockMvc.perform(post("/api/v1/masks")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMaskRequest(
                                "Python 导师",
                                "🐍",
                                "prompt",
                                "openai",
                                "gpt-4o-mini",
                                0.5,
                                1.0,
                                null,
                                List.of("python"),
                                false
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("9"));
    }

    @Test
    void updateReturnsUpdatedMask() throws Exception {
        when(maskService.update(eq(7L), eq(9L), any(UpdateMaskRequest.class))).thenReturn(new MaskResponse(
                "9",
                "7",
                "Updated",
                "🧪",
                "prompt",
                "openai",
                "gpt-4o-mini",
                0.2,
                1.0,
                null,
                List.of("test"),
                true,
                "2026-04-24T10:00:00Z",
                "2026-04-24T10:00:00Z"
        ));

        mockMvc.perform(patch("/api/v1/masks/9")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMaskRequest(
                                "Updated",
                                "🧪",
                                "prompt",
                                "openai",
                                "gpt-4o-mini",
                                0.2,
                                1.0,
                                null,
                                List.of("test"),
                                true
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/masks/9"))
                .andExpect(status().isNoContent());
    }
}
