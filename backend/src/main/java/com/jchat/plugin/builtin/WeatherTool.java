package com.jchat.plugin.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jchat.config.AppProperties;
import com.jchat.plugin.Tool;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class WeatherTool implements Tool {

    private static final int DEFAULT_DAYS = 3;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 7;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final AppProperties appProperties;
    private final WebClient webClient;

    public WeatherTool(AppProperties appProperties, WebClient llmWebClient) {
        this.appProperties = appProperties;
        this.webClient = llmWebClient;
    }

    @Override
    public String name() {
        return "weather";
    }

    @Override
    public String displayName() {
        return "Weather";
    }

    @Override
    public String description() {
        return "Get current weather and a short forecast for a city or location.";
    }

    @Override
    public JsonNode jsonSchema() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        var schema = factory.objectNode();
        var properties = factory.objectNode();
        properties.set("location", factory.objectNode()
                .put("type", "string")
                .put("description", "City name, district, or place name"));
        properties.set("days", factory.objectNode()
                .put("type", "integer")
                .put("description", "Forecast days between 1 and 7")
                .put("default", DEFAULT_DAYS)
                .put("minimum", MIN_DAYS)
                .put("maximum", MAX_DAYS));
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.set("required", factory.arrayNode().add("location"));
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode args, ToolContext context) {
        String location = args.path("location").asText(null);
        if (!StringUtils.hasText(location)) {
            return new ToolResult.Error("missing 'location'");
        }

        try {
            int days = clampDays(args.path("days").asInt(DEFAULT_DAYS));
            JsonNode geo = fetchGeocoding(location.trim());
            JsonNode firstResult = geo.path("results").isArray() && !geo.path("results").isEmpty()
                    ? geo.path("results").get(0)
                    : null;
            if (firstResult == null) {
                return new ToolResult.Error("location not found: " + location.trim());
            }

            double latitude = firstResult.path("latitude").asDouble(Double.NaN);
            double longitude = firstResult.path("longitude").asDouble(Double.NaN);
            if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
                return new ToolResult.Error("invalid weather location coordinates");
            }

            String resolvedName = buildResolvedName(firstResult);
            JsonNode weather = fetchWeather(latitude, longitude, days);
            return new ToolResult.Success(formatWeatherSummary(resolvedName, weather, days));
        } catch (RuntimeException exception) {
            return new ToolResult.Error("weather lookup failed");
        }
    }

    @Override
    public boolean isEnabled() {
        return appProperties.getTools().isWeatherEnabled();
    }

    @Override
    public String disabledReason() {
        return isEnabled() ? null : "APP_TOOLS_WEATHER_ENABLED=false";
    }

    private JsonNode fetchGeocoding(String location) {
        return webClient.get()
                .uri(appProperties.getTools().getWeatherGeocodingBaseUrl()
                        + "/v1/search?name={name}&count=1&language=en&format=json", location)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(REQUEST_TIMEOUT);
    }

    private JsonNode fetchWeather(double latitude, double longitude, int days) {
        return webClient.get()
                .uri(appProperties.getTools().getWeatherForecastBaseUrl()
                        + "/v1/forecast"
                        + "?latitude={latitude}&longitude={longitude}"
                        + "&current=temperature_2m,weather_code,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&forecast_days={days}&timezone=auto",
                        latitude,
                        longitude,
                        days)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(REQUEST_TIMEOUT);
    }

    private int clampDays(int requestedDays) {
        if (requestedDays < MIN_DAYS) {
            return MIN_DAYS;
        }
        return Math.min(requestedDays, MAX_DAYS);
    }

    private String buildResolvedName(JsonNode result) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, result.path("name").asText(null));
        addIfPresent(parts, result.path("admin1").asText(null));
        addIfPresent(parts, result.path("country").asText(null));
        return parts.isEmpty() ? "Unknown location" : String.join(", ", parts);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (StringUtils.hasText(value) && parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(value.trim()))) {
            parts.add(value.trim());
        }
    }

    private String formatWeatherSummary(String resolvedName, JsonNode weather, int days) {
        StringBuilder summary = new StringBuilder();
        JsonNode current = weather.path("current");
        summary.append(resolvedName);

        if (current.isObject()) {
            String temperature = current.path("temperature_2m").isNumber()
                    ? current.path("temperature_2m").asText() + "°C"
                    : "unknown";
            String condition = weatherCodeToText(current.path("weather_code").asInt(-1));
            String wind = current.path("wind_speed_10m").isNumber()
                    ? current.path("wind_speed_10m").asText() + " km/h"
                    : "unknown";
            summary.append(": current ").append(temperature)
                    .append(", ").append(condition)
                    .append(", wind ").append(wind);
        }

        JsonNode daily = weather.path("daily");
        JsonNode times = daily.path("time");
        JsonNode maxTemps = daily.path("temperature_2m_max");
        JsonNode minTemps = daily.path("temperature_2m_min");
        JsonNode codes = daily.path("weather_code");
        int forecastCount = Math.min(days, times.isArray() ? times.size() : 0);
        if (forecastCount > 0) {
            summary.append(". Forecast:");
            for (int index = 0; index < forecastCount; index++) {
                String date = times.get(index).asText("day-" + (index + 1));
                String max = maxTemps.isArray() && index < maxTemps.size() ? maxTemps.get(index).asText("?") : "?";
                String min = minTemps.isArray() && index < minTemps.size() ? minTemps.get(index).asText("?") : "?";
                int code = codes.isArray() && index < codes.size() ? codes.get(index).asInt(-1) : -1;
                summary.append(' ')
                        .append(date)
                        .append(' ')
                        .append(min)
                        .append("~")
                        .append(max)
                        .append("°C")
                        .append(' ')
                        .append(weatherCodeToText(code))
                        .append(';');
            }
        }

        return summary.toString().trim();
    }

    private String weatherCodeToText(int code) {
        return switch (code) {
            case 0 -> "clear";
            case 1, 2 -> "partly cloudy";
            case 3 -> "overcast";
            case 45, 48 -> "fog";
            case 51, 53, 55, 56, 57 -> "drizzle";
            case 61, 63, 65, 66, 67, 80, 81, 82 -> "rain";
            case 71, 73, 75, 77, 85, 86 -> "snow";
            case 95, 96, 99 -> "thunderstorm";
            default -> "unknown";
        };
    }
}
