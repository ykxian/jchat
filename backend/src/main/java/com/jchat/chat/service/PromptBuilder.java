package com.jchat.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jchat.conversation.entity.Conversation;
import com.jchat.conversation.entity.Message;
import com.jchat.conversation.entity.MessageRole;
import com.jchat.llm.dto.ChatMessage;
import com.jchat.mask.entity.Mask;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PromptBuilder {

    public List<ChatMessage> build(Conversation conversation, Mask mask, List<Message> history) {
        List<ChatMessage> prompt = new ArrayList<>();

        if (StringUtils.hasText(conversation.getSystemPrompt())) {
            prompt.add(ChatMessage.system(conversation.getSystemPrompt().trim()));
        }

        if (mask != null && StringUtils.hasText(mask.getSystemPrompt())) {
            prompt.add(ChatMessage.system(mask.getSystemPrompt().trim()));
        }

        for (Message message : history) {
            ChatMessage promptMessage = toPromptMessage(message);
            if (promptMessage != null) {
                prompt.add(promptMessage);
            }
        }
        return prompt;
    }

    private ChatMessage toPromptMessage(Message message) {
        String content = message.getContent();
        return switch (message.getRole()) {
            case system -> StringUtils.hasText(content) ? ChatMessage.system(content) : null;
            case assistant -> toAssistantMessage(message, content);
            case tool -> StringUtils.hasText(content) ? ChatMessage.tool(message.getToolCallId(), content) : null;
            case user -> StringUtils.hasText(content) ? ChatMessage.user(content) : null;
        };
    }

    private ChatMessage toAssistantMessage(Message message, String content) {
        JsonNode toolCalls = message.getToolCalls();
        if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
            List<ChatMessage.ToolCall> normalized = new ArrayList<>();
            for (JsonNode node : toolCalls) {
                String id = node.path("id").asText(null);
                String name = node.path("name").asText(null);
                JsonNode arguments = node.path("arguments");
                if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || arguments.isMissingNode()) {
                    continue;
                }
                normalized.add(new ChatMessage.ToolCall(id, name, arguments.deepCopy()));
            }
            if (!normalized.isEmpty()) {
                return ChatMessage.assistantToolCalls(normalized);
            }
        }

        return StringUtils.hasText(content) ? ChatMessage.assistant(content) : null;
    }
}
