package com.jchat.chat.service;

import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.entity.MessageRole;
import com.jchat.llm.dto.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PromptBuilder {

    public List<ChatMessage> build(Conversation conversation, List<Message> history) {
        List<ChatMessage> prompt = new ArrayList<>();

        if (StringUtils.hasText(conversation.getSystemPrompt())) {
            prompt.add(ChatMessage.system(conversation.getSystemPrompt().trim()));
        }

        for (Message message : history) {
            if (!StringUtils.hasText(message.getContent())) {
                continue;
            }
            prompt.add(toPromptMessage(message.getRole(), message.getContent()));
        }
        return prompt;
    }

    private ChatMessage toPromptMessage(MessageRole role, String content) {
        return switch (role) {
            case system -> ChatMessage.system(content);
            case assistant -> ChatMessage.assistant(content);
            case tool -> new ChatMessage("tool", content);
            case user -> ChatMessage.user(content);
        };
    }
}
