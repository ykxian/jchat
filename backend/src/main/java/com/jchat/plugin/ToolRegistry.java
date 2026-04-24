package com.jchat.plugin;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
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
}
