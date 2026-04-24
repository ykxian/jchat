package com.jchat.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name = "jchat";
    private String description = "多用户 AI 对话应用（仿 NextChat）";
    private final Cors cors = new Cors();
    private final Auth auth = new Auth();
    private final Crypto crypto = new Crypto();
    private final Chat chat = new Chat();
    private final Llm llm = new Llm();
    private final Tools tools = new Tools();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Cors getCors() {
        return cors;
    }

    public Auth getAuth() {
        return auth;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    public Chat getChat() {
        return chat;
    }

    public Llm getLlm() {
        return llm;
    }

    public Tools getTools() {
        return tools;
    }

    public static class Cors {

        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = new ArrayList<>(allowedOrigins);
        }
    }

    public static class Auth {

        private String jwtSecret =
                "MWYzMjU0NzY5OGFiY2RlZjAxMjM0NTY3ODlhYmNkZWYxZjMyNTQ3Njk4YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(7);
        private String refreshCookieName = "refresh";
        private boolean refreshCookieSecure;
        private String refreshCookieSameSite = "Lax";

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public String getRefreshCookieName() {
            return refreshCookieName;
        }

        public void setRefreshCookieName(String refreshCookieName) {
            this.refreshCookieName = refreshCookieName;
        }

        public boolean isRefreshCookieSecure() {
            return refreshCookieSecure;
        }

        public void setRefreshCookieSecure(boolean refreshCookieSecure) {
            this.refreshCookieSecure = refreshCookieSecure;
        }

        public String getRefreshCookieSameSite() {
            return refreshCookieSameSite;
        }

        public void setRefreshCookieSameSite(String refreshCookieSameSite) {
            this.refreshCookieSameSite = refreshCookieSameSite;
        }
    }

    public static class Crypto {

        private String key =
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    public static class Chat {

        private String defaultProvider = "openai";

        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(String defaultProvider) {
            this.defaultProvider = defaultProvider;
        }
    }

    public static class Llm {

        private int maxConnections = 200;
        private Duration pendingAcquireTimeout = Duration.ofSeconds(10);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration responseTimeout = Duration.ofSeconds(300);
        private DataSize maxInMemorySize = DataSize.ofMegabytes(10);
        private final Openai openai = new Openai();
        private final Anthropic anthropic = new Anthropic();
        private final Gemini gemini = new Gemini();

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public Duration getPendingAcquireTimeout() {
            return pendingAcquireTimeout;
        }

        public void setPendingAcquireTimeout(Duration pendingAcquireTimeout) {
            this.pendingAcquireTimeout = pendingAcquireTimeout;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public DataSize getMaxInMemorySize() {
            return maxInMemorySize;
        }

        public void setMaxInMemorySize(DataSize maxInMemorySize) {
            this.maxInMemorySize = maxInMemorySize;
        }

        public Openai getOpenai() {
            return openai;
        }

        public Anthropic getAnthropic() {
            return anthropic;
        }

        public Gemini getGemini() {
            return gemini;
        }
    }

    public static class Tools {

        private boolean calculatorEnabled = true;
        private boolean weatherEnabled = true;
        private String weatherGeocodingBaseUrl = "https://geocoding-api.open-meteo.com";
        private String weatherForecastBaseUrl = "https://api.open-meteo.com";
        private String httpFetchAllowlist = "";
        private int httpFetchTimeoutSeconds = 10;
        private int httpFetchMaxBytes = 2 * 1024 * 1024;
        private boolean webSearchEnabled = true;
        private String webSearchBaseUrl = "https://www.bing.com/search";

        public boolean isCalculatorEnabled() {
            return calculatorEnabled;
        }

        public void setCalculatorEnabled(boolean calculatorEnabled) {
            this.calculatorEnabled = calculatorEnabled;
        }

        public boolean isWeatherEnabled() {
            return weatherEnabled;
        }

        public void setWeatherEnabled(boolean weatherEnabled) {
            this.weatherEnabled = weatherEnabled;
        }

        public String getWeatherGeocodingBaseUrl() {
            return weatherGeocodingBaseUrl;
        }

        public void setWeatherGeocodingBaseUrl(String weatherGeocodingBaseUrl) {
            this.weatherGeocodingBaseUrl = weatherGeocodingBaseUrl;
        }

        public String getWeatherForecastBaseUrl() {
            return weatherForecastBaseUrl;
        }

        public void setWeatherForecastBaseUrl(String weatherForecastBaseUrl) {
            this.weatherForecastBaseUrl = weatherForecastBaseUrl;
        }

        public String getHttpFetchAllowlist() {
            return httpFetchAllowlist;
        }

        public void setHttpFetchAllowlist(String httpFetchAllowlist) {
            this.httpFetchAllowlist = httpFetchAllowlist;
        }

        public int getHttpFetchTimeoutSeconds() {
            return httpFetchTimeoutSeconds;
        }

        public void setHttpFetchTimeoutSeconds(int httpFetchTimeoutSeconds) {
            this.httpFetchTimeoutSeconds = httpFetchTimeoutSeconds;
        }

        public int getHttpFetchMaxBytes() {
            return httpFetchMaxBytes;
        }

        public void setHttpFetchMaxBytes(int httpFetchMaxBytes) {
            this.httpFetchMaxBytes = httpFetchMaxBytes;
        }

        public boolean isWebSearchEnabled() {
            return webSearchEnabled;
        }

        public void setWebSearchEnabled(boolean webSearchEnabled) {
            this.webSearchEnabled = webSearchEnabled;
        }

        public String getWebSearchBaseUrl() {
            return webSearchBaseUrl;
        }

        public void setWebSearchBaseUrl(String webSearchBaseUrl) {
            this.webSearchBaseUrl = webSearchBaseUrl;
        }
    }

    public static class Openai {

        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Anthropic {

        private String baseUrl = "https://api.anthropic.com";
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }

    public static class Gemini {

        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
