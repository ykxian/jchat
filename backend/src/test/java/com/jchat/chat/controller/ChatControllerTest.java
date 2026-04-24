package com.jchat.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.chat.dto.ChatCompletionMessage;
import com.jchat.chat.dto.ChatCompletionRequest;
import com.jchat.chat.dto.SseMessage;
import com.jchat.chat.service.ChatService;
import com.jchat.common.api.GlobalExceptionHandler;
import com.jchat.common.web.CorrelationIdFilter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;
    private ChatService chatService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        chatService = Mockito.mock(ChatService.class);
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
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
    void completeReturnsSseStream() throws Exception {
        ChatCompletionRequest requestBody = new ChatCompletionRequest(
                "42",
                "openai",
                "gpt-4o-mini",
                List.of(new ChatCompletionMessage("user", "hello")),
                0.7,
                1.0,
                256,
                null,
                List.of(),
                "high",
                null
        );

        SseEmitter emitter = new SseEmitter();
        when(chatService.complete(eq(7L), eq(requestBody))).thenReturn(emitter);

        Thread.startVirtualThread(() -> {
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.start("1001", "req-1")));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"start\"")));
    }
}
