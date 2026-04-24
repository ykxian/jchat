package com.jchat.plugin.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jchat.config.AppProperties;
import com.jchat.plugin.Tool;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolResult;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CalculatorTool implements Tool {

    private final AppProperties appProperties;

    public CalculatorTool(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Evaluate a math expression. Supports +, -, *, /, ^, sqrt, sin, cos, etc.";
    }

    @Override
    public JsonNode jsonSchema() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        var properties = factory.objectNode();
        properties.set("expression", factory.objectNode()
                .put("type", "string")
                .put("description", "Math expression to evaluate"));
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.set("required", factory.arrayNode().add("expression"));
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext context) {
        String expression = args.path("expression").asText(null);
        if (!StringUtils.hasText(expression)) {
            return new ToolResult.Error("missing 'expression'");
        }

        try {
            double value = new ExpressionBuilder(expression).build().evaluate();
            return new ToolResult.Success(expression + " = " + value);
        } catch (RuntimeException exception) {
            return new ToolResult.Error("invalid expression: " + exception.getMessage());
        }
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getTools().isCalculatorEnabled();
    }
}
