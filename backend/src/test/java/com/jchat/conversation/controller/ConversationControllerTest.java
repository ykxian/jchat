package com.jchat.conversation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.api.GlobalExceptionHandler;
import com.jchat.common.jpa.CursorPage;
import com.jchat.common.web.CorrelationIdFilter;
import com.jchat.conversation.dto.ConversationResponse;
import com.jchat.conversation.dto.CreateConversationRequest;
import com.jchat.conversation.dto.MessageResponse;
import com.jchat.conversation.dto.UpdateConversationRequest;
import com.jchat.conversation.service.ConversationService;
import com.jchat.conversation.service.MessageService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private ConversationService conversationService;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        conversationService = Mockito.mock(ConversationService.class);
        messageService = Mockito.mock(MessageService.class);
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(conversationService, messageService))
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
        when(conversationService.list(7L, null, 20, false, null)).thenReturn(new CursorPage<>(
                List.of(new ConversationResponse(
                        "42",
                        "Chat",
                        "openai",
                        "gpt-4o-mini",
                        null,
                        false,
                        false,
                        null,
                        0,
                        "2026-04-23T10:15:30Z",
                        "2026-04-23T10:15:30Z"
                )),
                null
        ));

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.items[0].id").value("42"))
                .andExpect(jsonPath("$.items[0].provider").value("openai"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void createReturnsCreatedConversation() throws Exception {
        when(conversationService.create(eq(7L), any(CreateConversationRequest.class))).thenReturn(new ConversationResponse(
                "42",
                "Chat",
                "openai",
                "gpt-4o-mini",
                "be precise",
                false,
                false,
                null,
                0,
                "2026-04-23T10:15:30Z",
                "2026-04-23T10:15:30Z"
        ));

        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateConversationRequest("Chat", "openai", "gpt-4o-mini", "be precise"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.title").value("Chat"));
    }

    @Test
    void updateReturnsUpdatedConversation() throws Exception {
        when(conversationService.update(eq(7L), eq(42L), any(UpdateConversationRequest.class))).thenReturn(new ConversationResponse(
                "42",
                "Renamed",
                "openai",
                "gpt-4.1",
                null,
                true,
                false,
                null,
                0,
                "2026-04-23T10:15:30Z",
                "2026-04-23T10:16:30Z"
        ));

        mockMvc.perform(patch("/api/v1/conversations/42")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateConversationRequest("Renamed", true, null, null, null, "gpt-4.1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/conversations/42"))
                .andExpect(status().isNoContent());
    }

    @Test
    void messagesReturnsCursorPage() throws Exception {
        when(messageService.list(7L, 42L, null, 50)).thenReturn(new CursorPage<>(
                List.of(new MessageResponse(
                        "1001",
                        "assistant",
                        "hello",
                        null,
                        null,
                        null,
                        12,
                        28,
                        List.of(),
                        "2026-04-23T10:15:30Z"
                )),
                null
        ));

        mockMvc.perform(get("/api/v1/conversations/42/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("1001"))
                .andExpect(jsonPath("$.items[0].role").value("assistant"));
    }
}
