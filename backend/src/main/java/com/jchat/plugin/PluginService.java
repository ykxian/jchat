package com.jchat.plugin;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PluginService {

    private final ToolRegistry toolRegistry;

    public PluginService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public PluginListResponse list() {
        List<PluginInfo> items = toolRegistry.all().stream()
                .map(tool -> new PluginInfo(
                        tool.name(),
                        tool.displayName(),
                        tool.description(),
                        tool.isEnabled(),
                        tool.disabledReason(),
                        tool.jsonSchema()
                ))
                .toList();
        return new PluginListResponse(items);
    }
}
