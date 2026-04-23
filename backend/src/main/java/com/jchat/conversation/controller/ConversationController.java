package com.jchat.conversation.controller;

import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.common.jpa.CursorPage;
import com.jchat.conversation.dto.ConversationResponse;
import com.jchat.conversation.dto.CreateConversationRequest;
import com.jchat.conversation.dto.MessageResponse;
import com.jchat.conversation.dto.UpdateConversationRequest;
import com.jchat.conversation.service.ConversationService;
import com.jchat.conversation.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    @GetMapping
    public CursorPage<ConversationResponse> list(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "false") Boolean archived,
            @RequestParam(required = false) Boolean pinned
    ) {
        return conversationService.list(principal.userId(), cursor, limit, archived, pinned);
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request
    ) {
        return ResponseEntity.status(201).body(conversationService.create(principal.userId(), request));
    }

    @GetMapping("/{id}")
    public ConversationResponse get(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        return conversationService.get(principal.userId(), id);
    }

    @PatchMapping("/{id}")
    public ConversationResponse update(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        return conversationService.update(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id
    ) {
        conversationService.delete(principal.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public CursorPage<MessageResponse> messages(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit
    ) {
        return messageService.list(principal.userId(), id, cursor, limit);
    }
}
