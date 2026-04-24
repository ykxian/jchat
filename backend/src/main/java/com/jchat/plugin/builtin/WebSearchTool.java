package com.jchat.plugin.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jchat.config.AppProperties;
import com.jchat.plugin.Tool;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolResult;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.xml.sax.InputSource;

@Component
public class WebSearchTool implements Tool {

    private static final int DEFAULT_RESULTS = 5;
    private static final int MAX_RESULTS = 10;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final AppProperties appProperties;
    private final WebClient webClient;

    public WebSearchTool(AppProperties appProperties, WebClient llmWebClient) {
        this.appProperties = appProperties;
        this.webClient = llmWebClient;
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String displayName() {
        return "Web Search";
    }

    @Override
    public String description() {
        return "Search the web for up-to-date information and return the top results with titles, links, and snippets.";
    }

    @Override
    public JsonNode jsonSchema() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        var properties = factory.objectNode();
        properties.set("query", factory.objectNode()
                .put("type", "string")
                .put("description", "Search query"));
        properties.set("num", factory.objectNode()
                .put("type", "integer")
                .put("description", "Number of results to return")
                .put("default", DEFAULT_RESULTS)
                .put("minimum", 1)
                .put("maximum", MAX_RESULTS));
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.set("required", factory.arrayNode().add("query"));
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext context) {
        String query = args.path("query").asText(null);
        if (!StringUtils.hasText(query)) {
            return new ToolResult.Error("missing 'query'");
        }

        int limit = clampLimit(args.path("num").asInt(DEFAULT_RESULTS));
        try {
            String queryText = query.trim();
            String xml = fetchSearchXml(appProperties.getTools().getWebSearchBaseUrl(), queryText);
            List<SearchResult> results = parseResults(xml, limit);

            String baseUrl = appProperties.getTools().getWebSearchBaseUrl();
            if (results.isEmpty() && baseUrl.contains("www.bing.com")) {
                xml = fetchSearchXml(baseUrl.replace("www.bing.com", "cn.bing.com"), queryText);
                results = parseResults(xml, limit);
            }

            if (results.isEmpty()) {
                return new ToolResult.Error("no results");
            }

            List<String> lines = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) {
                SearchResult result = results.get(index);
                lines.add("%d. %s\n%s\n%s".formatted(index + 1, result.title(), result.link(), result.snippet()));
            }
            return new ToolResult.Success("Search results for \"" + queryText + "\":\n" + String.join("\n\n", lines));
        } catch (Exception exception) {
            return new ToolResult.Error("web search failed");
        }
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getTools().isWebSearchEnabled();
    }

    @Override
    public String disabledReason() {
        return isEnabled() ? null : "APP_TOOLS_WEB_SEARCH_ENABLED=false";
    }

    private int clampLimit(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, MAX_RESULTS);
    }

    private String fetchSearchXml(String baseUrl, String query) {
        return webClient.get()
                .uri(baseUrl + "?format=rss&q={query}", query)
                .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML)
                .retrieve()
                .bodyToMono(String.class)
                .block(REQUEST_TIMEOUT);
    }

    private List<SearchResult> parseResults(String xml, int limit) throws Exception {
        if (!StringUtils.hasText(xml)) {
            return List.of();
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var itemNodes = document.getElementsByTagName("item");
        List<SearchResult> results = new ArrayList<>();
        for (int index = 0; index < itemNodes.getLength() && results.size() < limit; index++) {
            var item = itemNodes.item(index);
            var children = item.getChildNodes();
            String title = null;
            String link = null;
            String description = null;
            for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
                var child = children.item(childIndex);
                switch (child.getNodeName()) {
                    case "title" -> title = normalize(child.getTextContent());
                    case "link" -> link = normalize(child.getTextContent());
                    case "description" -> description = normalize(child.getTextContent());
                    default -> {
                    }
                }
            }
            if (StringUtils.hasText(title) && StringUtils.hasText(link)) {
                results.add(new SearchResult(title, link, StringUtils.hasText(description) ? description : "(no snippet)"));
            }
        }
        return results;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private record SearchResult(String title, String link, String snippet) {
    }
}
