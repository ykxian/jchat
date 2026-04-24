package com.jchat.chat.service;

import com.jchat.chat.dto.SseMessage;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseEventWriter {

    public void sendMessage(SseEmitter emitter, SseMessage message) throws IOException {
        emitter.send(SseEmitter.event()
                .name("message")
                .data(message));
    }

    public void sendError(SseEmitter emitter, SseMessage message) throws IOException {
        emitter.send(SseEmitter.event()
                .name("error")
                .data(message));
    }
}
