package com.jchat.plugin;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.llm.dto.ChatRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ToolRegistry {

    private final Map<String, Tool> byName;

    public ToolRegistry(List<Tool> tools) {
        this.byName = tools.stream().collect(Collectors.toUnmodifiableMap(Tool::name, Function.identity()));
    }

    public Tool get(String name) {
        Tool tool = byName.get(name);
        if (tool == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown tool: " + name);
        }
        return tool;
    }

    public List<ChatRequest.ToolSpec> listEnabledToolSpecs() {
        return byName.values().stream()
                .filter(Tool::isEnabled)
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .map(tool -> new ChatRequest.ToolSpec(tool.name(), tool.description(), tool.jsonSchema()))
                .toList();
    }

    public List<Tool> all() {
        return byName.values().stream()
                .sorted((left, right) -> left.name().compareTo(right.name()))
                .toList();
    }
}
