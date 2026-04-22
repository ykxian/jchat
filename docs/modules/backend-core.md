# Backend Core Module

> Spring Boot 应用骨架：配置、通用组件、全局异常、日志、健康检查、WebClient、Jackson、API 文档。
>
> Context：所有业务模块（auth、chat、llm 等）共用的基础设施；业务代码不应直接写配置。

---

## 1. 职责边界

**做**：
- 应用启动类
- 全局配置 bean（Security 除外，见 auth 模块）：WebClient、Jackson、OpenAPI、Async、虚拟线程
- 通用异常体系（`ApiException` + `GlobalExceptionHandler`）
- MDC 注入（request_id、user_id）
- 健康检查 / Actuator

**不做**：
- 业务逻辑（都在各子包）
- 具体的 Security 配置（见 auth 模块）

---

## 2. 技术栈（版本细化）

| 层 | 选型 | 版本 |
|---|---|---|
| JDK | Temurin | 21.0.x |
| 框架 | Spring Boot | 3.4.x |
| 构建 | Gradle | 8.x, Kotlin DSL |
| Web | spring-web (MVC) | BOM |
| 反应式 HTTP 客户端 | spring-webflux (仅 WebClient) | BOM |
| ORM | spring-data-jpa (Hibernate 6) | BOM |
| 迁移 | Flyway 10 | 10.x |
| JSON | hypersistence-utils | 3.9.0 |
| 日志 | logback + logstash-encoder | 7.4 |
| API 文档 | springdoc-openapi | 2.6.0 |

完整依赖见根规划"附录 B"或 `backend/build.gradle.kts`。

---

## 3. 仓库结构

```
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── gradle/
├── gradlew, gradlew.bat
└── src/
    ├── main/
    │   ├── java/com/jchat/
    │   │   ├── JchatApplication.java
    │   │   ├── config/
    │   │   │   ├── WebClientConfig.java
    │   │   │   ├── JacksonConfig.java
    │   │   │   ├── AsyncConfig.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── RedisConfig.java
    │   │   │   └── CorsConfig.java
    │   │   ├── common/
    │   │   │   ├── api/
    │   │   │   │   ├── ApiException.java
    │   │   │   │   ├── ErrorCode.java
    │   │   │   │   ├── ErrorResponse.java
    │   │   │   │   └── GlobalExceptionHandler.java
    │   │   │   ├── jpa/
    │   │   │   │   ├── BaseEntity.java        # id + createdAt + updatedAt + deletedAt
    │   │   │   │   └── CursorPage.java        # cursor 分页工具
    │   │   │   ├── web/
    │   │   │   │   ├── CorrelationIdFilter.java
    │   │   │   │   └── AuthenticatedUserResolver.java   # @CurrentUser 参数解析
    │   │   │   ├── security/
    │   │   │   │   └── CurrentUser.java       # 自定义注解
    │   │   │   ├── ratelimit/
    │   │   │   │   ├── RateLimitService.java
    │   │   │   │   └── RateLimited.java       # 注解
    │   │   │   └── util/
    │   │   │       ├── Ids.java
    │   │   │       ├── Clock.java
    │   │   │       └── Json.java
    │   │   ├── auth/                          # 见 modules/auth.md
    │   │   ├── apikey/
    │   │   ├── conversation/                  # 见 modules/conversations.md
    │   │   ├── chat/
    │   │   ├── llm/                           # 见 modules/llm-providers.md
    │   │   ├── mask/                          # 见 modules/masks-prompts.md
    │   │   ├── plugin/                        # 见 modules/plugins.md
    │   │   └── file/                          # 见 modules/files-rag.md
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── db/migration/
    │       │   ├── V1__init_schema.sql
    │       │   ├── V2__masks_and_plugins.sql
    │       │   ├── V3__files_and_attachments.sql
    │       │   ├── V4__usage_stats.sql
    │       │   ├── V5__seed_masks_and_plugins.sql
    │       │   └── V6__pgvector_extension.sql
    │       ├── masks/                         # 内置 mask JSON 源（转成 V5 SQL 的辅助）
    │       └── logback-spring.xml
    └── test/
        └── java/com/jchat/
            ├── IntegrationTestBase.java       # Testcontainers 基类
            └── ... 各模块测试
```

---

## 4. 启动类

```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class JchatApplication {
    public static void main(String[] args) {
        SpringApplication.run(JchatApplication.class, args);
    }
}
```

`application.yml` 中开启虚拟线程：`spring.threads.virtual.enabled: true`。

`AppProperties` 映射 `app.*` 所有配置（type-safe）。

---

## 5. 配置 bean

### 5.1 `WebClientConfig`

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient llmWebClient(AppProperties props) {
        var httpClient = HttpClient.create(ConnectionProvider.builder("llm")
                .maxConnections(200)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build())
            .responseTimeout(Duration.ofSeconds(300))   // 300s 硬超时
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .compress(false);                           // LLM 流式不要 gzip

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))  // 10MB per 响应
            .build();
    }
}
```

### 5.2 `JacksonConfig`

```java
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .modules(new JavaTimeModule())
            .timeZone(TimeZone.getTimeZone("UTC"))
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

**ID 字段序列化为字符串**：Entity 的 `Long id` 用 `@JsonSerialize(using = ToStringSerializer.class)`。或全局：

```java
@Bean
public Module longToStringModule() {
    var m = new SimpleModule();
    m.addSerializer(Long.class, ToStringSerializer.instance);
    m.addSerializer(Long.TYPE, ToStringSerializer.instance);
    return m;
}
```

（但这会影响所有 Long，包括 token 计数。取舍：要么 DTO 里用 String id，要么用 `@JsonSerialize(ToStringSerializer.class)` 精准注解。推荐**后者**，DTO 字段 `String id` 在 mapper 里转。）

### 5.3 `AsyncConfig`

```java
@Configuration
public class AsyncConfig {
    @Bean("virtualThreadExecutor")
    public AsyncTaskExecutor virtualThreadExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

`@Async("virtualThreadExecutor")` 使其生效；如 Tika 抽文本。

### 5.4 `OpenApiConfig`

```java
@Configuration
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
    @Bean
    public OpenAPI api() {
        return new OpenAPI().info(new Info()
            .title("jchat API").version("v1")
            .description("多用户 AI 对话应用（仿 NextChat）"));
    }
}
```

Controller 类加 `@SecurityRequirement(name = "bearer")`。

### 5.5 `RedisConfig`

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory cf) {
        var t = new RedisTemplate<String, String>();
        t.setConnectionFactory(cf);
        t.setKeySerializer(RedisSerializer.string());
        t.setValueSerializer(RedisSerializer.string());
        return t;
    }

    @Bean
    public ProxyManager<String> bucketProxyManager(RedisConnectionFactory cf) {
        // Bucket4j + Redis, for rate limit
        return LettuceBasedProxyManager.builderFor(cf).build();
    }
}
```

### 5.6 `CorsConfig`（dev 专用）

```java
@Configuration
@Profile("dev")
public class DevCorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true);
    }
}
```

Prod 同源（nginx 反代），不需要 CORS。

---

## 6. 通用异常体系

### 6.1 `ErrorCode`

```java
public enum ErrorCode {
    AUTH_INVALID(401),
    AUTH_EXPIRED(401),
    AUTH_REFRESH_INVALID(401),
    VALIDATION_FAILED(400),
    NOT_FOUND(404),
    FORBIDDEN(403),
    CONFLICT(409),
    RATE_LIMITED(429),
    LLM_UPSTREAM_ERROR(502),
    LLM_UPSTREAM_TIMEOUT(504),
    INTERNAL_ERROR(500);

    private final int httpStatus;
    // constructor, getter ...
}
```

### 6.2 `ApiException`

```java
public class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Object details;

    public ApiException(ErrorCode code, String message) { this(code, message, null); }
    public ApiException(ErrorCode code, String message, Object details) {
        super(message);
        this.code = code;
        this.details = details;
    }
    // getters ...
}
```

### 6.3 `GlobalExceptionHandler`

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        log.info("ApiException: code={} msg={}", ex.getCode(), ex.getMessage());
        return build(ex.getCode(), ex.getMessage(), ex.getDetails(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of("field", fe.getField(), "message", fe.getDefaultMessage()))
            .toList();
        return build(ErrorCode.VALIDATION_FAILED, "validation failed", fields, req);
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(WebClientResponseException ex, HttpServletRequest req) {
        log.warn("LLM upstream error: status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return build(ErrorCode.LLM_UPSTREAM_ERROR, "LLM upstream error", null, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(ErrorCode.FORBIDDEN, "forbidden", null, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, "internal error", null, req);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String msg, Object details, HttpServletRequest req) {
        var body = new ErrorResponse(code.name(), msg, details, MDC.get("requestId"));
        return ResponseEntity.status(code.getHttpStatus()).body(body);
    }
}
```

---

## 7. Correlation ID / MDC

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var reqId = Optional.ofNullable(req.getHeader("X-Request-Id")).orElseGet(() -> UUID.randomUUID().toString());
        MDC.put("requestId", reqId);
        res.setHeader("X-Request-Id", reqId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

鉴权成功后，在 `JwtAuthenticationFilter` 里 `MDC.put("userId", String.valueOf(user.getId()));`。

---

## 8. 当前用户解析

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)   // springdoc 忽略
public @interface CurrentUser {}

@Component
public class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {
    private final UserRepository users;

    @Override public boolean supportsParameter(MethodParameter p) {
        return p.hasParameterAnnotation(CurrentUser.class) && User.class.isAssignableFrom(p.getParameterType());
    }

    @Override public Object resolveArgument(...) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ApiException(AUTH_INVALID, "unauthenticated");
        var userId = ((JwtPrincipal) auth.getPrincipal()).userId();
        return users.findById(userId).orElseThrow(() -> new ApiException(AUTH_INVALID, "user not found"));
    }
}
```

Controller 用法：
```java
@PostMapping
public Response foo(@CurrentUser User user, @RequestBody Req req) { ... }
```

---

## 9. 健康检查 + Actuator

**public**：`GET /api/v1/health` → `{"status": "UP"}`（手写 controller，不依赖 Actuator）

**Actuator**：prod 只暴露 `/actuator/health`：
```yaml
management:
  endpoints.web.exposure.include: health
  endpoint.health.show-details: when_authorized
```

---

## 10. 日志 (`logback-spring.xml`)

```xml
<configuration>
  <springProfile name="dev">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level [%X{requestId:-}] [%X{userId:-}] %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="CONSOLE"/></root>
    <logger name="com.jchat" level="DEBUG"/>
  </springProfile>

  <springProfile name="prod">
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
      <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>requestId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
      </encoder>
    </appender>
    <root level="INFO"><appender-ref ref="JSON"/></root>
  </springProfile>
</configuration>
```

**敏感字段禁止打印**：密码、refresh token 明文、用户 API key 明文。review 代码时 grep `log.`。

---

## 11. 限流（Bucket4j + Redis）

```java
public class RateLimitService {
    private final ProxyManager<String> pm;

    public void tryAcquire(String key, long tokens, long refillPerMin) {
        var config = BucketConfiguration.builder()
            .addLimit(limit -> limit.capacity(tokens).refillGreedy(refillPerMin, Duration.ofMinutes(1)))
            .build();
        var bucket = pm.builder().build(key, () -> config);
        if (!bucket.tryConsume(1)) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "rate limited");
        }
    }
}
```

用法：
```java
rateLimit.tryAcquire("chat:" + user.getId(), 60, 60);  // 60/min
rateLimit.tryAcquire("register:ip:" + ip, 10, 10);     // 10/hour（调整 duration）
```

---

## 12. `application.yml` 主配置

```yaml
spring:
  application.name: jchat
  threads.virtual.enabled: true
  profiles.active: ${SPRING_PROFILES_ACTIVE:dev}
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari.maximum-pool-size: 20
  jpa:
    hibernate.ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
      hibernate.format_sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true
  data.redis.url: ${REDIS_URL}
  servlet.multipart:
    max-file-size: 50MB
    max-request-size: 60MB

app:
  jwt:
    secret: ${JWT_SECRET}
    access-ttl: PT15M
    refresh-ttl: P7D
  crypto:
    key: ${APP_CRYPTO_KEY}
  storage:
    root: ${STORAGE_ROOT:/var/lib/jchat/files}
  chat:
    daily-quota: ${APP_CHAT_DAILY_QUOTA:100}
    max-tool-depth: ${APP_TOOL_ROUNDTRIP_MAX_DEPTH:5}
    max-history-messages: 30
  llm:
    openai:
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
      api-key: ${OPENAI_API_KEY:}
      default-model: gpt-4o-mini
    anthropic:
      base-url: ${ANTHROPIC_BASE_URL:https://api.anthropic.com}
      api-key: ${ANTHROPIC_API_KEY:}
      default-model: claude-sonnet-4-6
    gemini:
      base-url: ${GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta}
      api-key: ${GEMINI_API_KEY:}
      default-model: gemini-1.5-pro
  tools:
    http-fetch:
      allowlist: ${APP_TOOLS_HTTP_FETCH_ALLOWLIST:}
      timeout-seconds: 10
      max-bytes: 2097152
    serpapi:
      key: ${APP_TOOLS_SERPAPI_KEY:}

logging:
  level:
    root: INFO
    com.jchat: INFO
```

Dev 覆盖：`application-dev.yml` 里放松 CORS、开 `hibernate.format_sql: true`。
Prod 覆盖：`application-prod.yml` 关 Actuator 多数 endpoint、更短的 `hikari.leak-detection-threshold`。

---

## 13. 构建与打包

### Gradle
```kotlin
plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.6"
    java
}

group = "com.jchat"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

dependencies { /* 见附录 B */ }

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

tasks.withType<Test> { useJUnitPlatform() }

// Integration test 分离
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
        runtimeClasspath += output + compileClasspath
    }
}
tasks.register<Test>("integrationTest") {
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    useJUnitPlatform()
}
```

### bootJar 产物
`backend/build/libs/jchat-<version>.jar` — fat jar，含所有依赖。

---

## 14. 测试基类

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("jchat").withUsername("jchat").withPassword("jchat");

    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("app.jwt.secret", () -> "dGVzdC1qd3Qtc2VjcmV0LTQ4LWJ5dGVzLXBsYWNlaG9sZGVy...");
        r.add("app.crypto.key", () -> "dGVzdC1hZXMta2V5LTMyLWJ5dGVzLXBsY2M=");
    }

    @Autowired protected MockMvc mvc;
}
```
