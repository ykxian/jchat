package com.jchat.plugin.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jchat.config.AppProperties;
import com.jchat.plugin.Tool;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolResult;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.HtmlUtils;

@Component
public class HttpFetchTool implements Tool {

    private static final Pattern SCRIPT_TAGS = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern STYLE_TAGS = Pattern.compile("(?is)<style[^>]*>.*?</style>");
    private static final Pattern HTML_TAGS = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern TITLE_TAG = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int MAX_TEXT_LENGTH = 10_000;

    private final AppProperties appProperties;
    private final WebClient webClient;

    public HttpFetchTool(AppProperties appProperties, WebClient llmWebClient) {
        this.appProperties = appProperties;
        this.webClient = llmWebClient;
    }

    @Override
    public String name() {
        return "http_fetch";
    }

    @Override
    public String displayName() {
        return "HTTP Fetch";
    }

    @Override
    public String description() {
        return "Fetch and extract text from an allowlisted URL. Use this for documentation or web pages when an exact source is needed.";
    }

    @Override
    public JsonNode jsonSchema() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        var properties = factory.objectNode();
        properties.set("url", factory.objectNode()
                .put("type", "string")
                .put("description", "Absolute http or https URL to fetch"));
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.set("required", factory.arrayNode().add("url"));
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext context) {
        String rawUrl = args.path("url").asText(null);
        if (!StringUtils.hasText(rawUrl)) {
            return new ToolResult.Error("missing 'url'");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException exception) {
            return new ToolResult.Error("invalid url");
        }

        String scheme = uri.getScheme();
        if (!StringUtils.hasText(scheme) || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            return new ToolResult.Error("only http and https URLs are allowed");
        }

        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            return new ToolResult.Error("missing host");
        }
        if (!isAllowlisted(host)) {
            return new ToolResult.Error("domain not in allowlist: " + host);
        }

        try {
            FetchResponse response = webClient.get()
                    .uri(uri)
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(byte[].class)
                            .defaultIfEmpty(new byte[0])
                            .map(body -> new FetchResponse(clientResponse.headers().contentType().orElse(null), body)))
                    .block(Duration.ofSeconds(appProperties.getTools().getHttpFetchTimeoutSeconds()));

            if (response == null || response.body().length == 0) {
                return new ToolResult.Error("empty response");
            }
            if (response.body().length > appProperties.getTools().getHttpFetchMaxBytes()) {
                return new ToolResult.Error("response too large");
            }

            String text = decodeBody(response.contentType(), response.body());
            String extracted = extractText(response.contentType(), text);
            if (!StringUtils.hasText(extracted)) {
                return new ToolResult.Error("no readable text found");
            }
            if (extracted.length() > MAX_TEXT_LENGTH) {
                extracted = extracted.substring(0, MAX_TEXT_LENGTH) + " ... [truncated]";
            }

            String title = extractTitle(text);
            String summary = StringUtils.hasText(title)
                    ? "Title: " + title + "\n\n" + extracted
                    : extracted;
            return new ToolResult.Success(summary);
        } catch (RuntimeException exception) {
            return new ToolResult.Error("http fetch failed");
        }
    }

    @Override
    public boolean isEnabled() {
        return !allowlistEntries().isEmpty();
    }

    @Override
    public String disabledReason() {
        return isEnabled() ? null : "APP_TOOLS_HTTP_FETCH_ALLOWLIST is empty";
    }

    private boolean isAllowlisted(String host) {
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        return allowlistEntries().stream()
                .anyMatch(entry -> normalizedHost.equals(entry) || normalizedHost.endsWith("." + entry));
    }

    private Set<String> allowlistEntries() {
        String raw = appProperties.getTools().getHttpFetchAllowlist();
        if (!StringUtils.hasText(raw)) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String decodeBody(MediaType contentType, byte[] body) {
        Charset charset = contentType != null && contentType.getCharset() != null
                ? contentType.getCharset()
                : StandardCharsets.UTF_8;
        return new String(body, charset);
    }

    private String extractText(MediaType contentType, String body) {
        if (contentType != null && isTextLike(contentType) && !looksLikeHtml(body)) {
            return normalizeWhitespace(body);
        }

        String withoutScripts = SCRIPT_TAGS.matcher(body).replaceAll(" ");
        String withoutStyles = STYLE_TAGS.matcher(withoutScripts).replaceAll(" ");
        String plainText = HTML_TAGS.matcher(withoutStyles).replaceAll(" ");
        return normalizeWhitespace(HtmlUtils.htmlUnescape(plainText));
    }

    private boolean isTextLike(MediaType contentType) {
        return contentType.isCompatibleWith(MediaType.TEXT_PLAIN)
                || contentType.isCompatibleWith(MediaType.APPLICATION_JSON)
                || contentType.isCompatibleWith(MediaType.APPLICATION_XML)
                || contentType.isCompatibleWith(MediaType.TEXT_XML);
    }

    private boolean looksLikeHtml(String body) {
        String preview = body.length() > 200 ? body.substring(0, 200) : body;
        String normalized = preview.toLowerCase(Locale.ROOT);
        return normalized.contains("<html") || normalized.contains("<body") || normalized.contains("<title") || normalized.contains("<div");
    }

    private String extractTitle(String body) {
        var matcher = TITLE_TAG.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return normalizeWhitespace(HtmlUtils.htmlUnescape(matcher.group(1)));
    }

    private String normalizeWhitespace(String value) {
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    private record FetchResponse(MediaType contentType, byte[] body) {
    }
}
