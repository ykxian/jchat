package com.jchat.chat.controller;

import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.chat.dto.ChatCompletionRequest;
import com.jchat.chat.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(path = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter complete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ChatCompletionRequest request
    ) {
        return chatService.complete(principal.userId(), request);
    }
}
