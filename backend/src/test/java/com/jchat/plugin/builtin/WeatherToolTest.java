package com.jchat.plugin.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.config.AppProperties;
import com.jchat.plugin.ToolContext;
import com.jchat.plugin.ToolResult;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getTools().setWeatherGeocodingBaseUrl("https://geo.example.test");
        appProperties.getTools().setWeatherForecastBaseUrl("https://weather.example.test");
    }

    @Test
    void executeReturnsFormattedWeatherSummary() throws Exception {
        WeatherTool tool = new WeatherTool(appProperties, mockWebClient(successExchangeFunction()));

        ToolResult result = tool.execute(args("""
                {
                  "location": "Shanghai",
                  "days": 2
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertTrue(result instanceof ToolResult.Success);
        String text = ((ToolResult.Success) result).text();
        assertTrue(text.contains("Shanghai, China"));
        assertTrue(text.contains("current 22.4°C, clear, wind 5.6 km/h"));
        assertTrue(text.contains("2026-04-24 18.1~27.2°C clear;"));
        assertTrue(text.contains("2026-04-25 16.5~24.0°C rain;"));
    }

    @Test
    void executeReturnsNotFoundWhenLocationMissingFromGeocoding() throws Exception {
        WeatherTool tool = new WeatherTool(appProperties, mockWebClient(notFoundExchangeFunction()));

        ToolResult result = tool.execute(args("""
                {
                  "location": "Nowhere"
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertEquals(new ToolResult.Error("location not found: Nowhere"), result);
    }

    @Test
    void executeRejectsMissingLocation() throws Exception {
        WeatherTool tool = new WeatherTool(appProperties, mockWebClient(successExchangeFunction()));

        ToolResult result = tool.execute(args("""
                {
                  "days": 3
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertEquals(new ToolResult.Error("missing 'location'"), result);
    }

    @Test
    void executeClampsForecastDaysToSeven() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        ExchangeFunction exchangeFunction = request -> {
            int count = requestCount.incrementAndGet();
            if (count == 1) {
                return Mono.just(jsonResponse("""
                        {
                          "results": [{
                            "name": "Shanghai",
                            "admin1": "Shanghai",
                            "country": "China",
                            "latitude": 31.23,
                            "longitude": 121.47
                          }]
                        }
                        """));
            }
            assertTrue(request.url().toString().contains("forecast_days=7"));
            return Mono.just(jsonResponse("""
                    {
                      "current": {
                        "temperature_2m": 22.4,
                        "weather_code": 0,
                        "wind_speed_10m": 5.6
                      },
                      "daily": {
                        "time": ["2026-04-24"],
                        "temperature_2m_max": [27.2],
                        "temperature_2m_min": [18.1],
                        "weather_code": [0]
                      }
                    }
                    """));
        };

        WeatherTool tool = new WeatherTool(appProperties, mockWebClient(exchangeFunction));
        ToolResult result = tool.execute(args("""
                {
                  "location": "Shanghai",
                  "days": 99
                }
                """), new ToolContext(7L, 42L, "req-1"));

        assertTrue(result instanceof ToolResult.Success);
    }

    private JsonNode args(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private WebClient mockWebClient(ExchangeFunction exchangeFunction) {
        return WebClient.builder().exchangeFunction(exchangeFunction).build();
    }

    private ExchangeFunction successExchangeFunction() {
        AtomicInteger requestCount = new AtomicInteger();
        return request -> Mono.just(requestCount.incrementAndGet() == 1
                ? jsonResponse("""
                {
                  "results": [{
                    "name": "Shanghai",
                    "admin1": "Shanghai",
                    "country": "China",
                    "latitude": 31.23,
                    "longitude": 121.47
                  }]
                }
                """)
                : jsonResponse("""
                {
                  "current": {
                    "temperature_2m": 22.4,
                    "weather_code": 0,
                    "wind_speed_10m": 5.6
                  },
                  "daily": {
                    "time": ["2026-04-24", "2026-04-25"],
                    "temperature_2m_max": [27.2, 24.0],
                    "temperature_2m_min": [18.1, 16.5],
                    "weather_code": [0, 61]
                  }
                }
                """));
    }

    private ExchangeFunction notFoundExchangeFunction() {
        return request -> Mono.just(jsonResponse("""
                {
                  "results": []
                }
                """));
    }

    private ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
