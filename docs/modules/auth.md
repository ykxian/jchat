# Auth Module

> 账号、注册、登录、JWT 签发、刷新、登出、当前用户查询、改密。
>
> Context：系统入口。所有其他模块依赖本模块提供的 `User` 实体与 `SecurityContext` 注入。

---

## 1. 职责边界

**做**：
- 邮箱 + 密码注册 / 登录
- JWT access token 签发与校验（`Authorization: Bearer`）
- Refresh token 签发、轮换、吊销（HttpOnly cookie）
- Spring Security 配置 + JWT 过滤器
- 用户信息 CRUD（昵称 / 头像 / 改密）
- 注册 / 登录限流

**不做**：
- 用户 API key 管理（见 `apikey/`）
- 邮件发送（v1 不做；`emailVerified` 保留）
- OAuth（v2）

---

## 2. 数据模型（关联）

详见 [DATA-MODEL.md](../DATA-MODEL.md)：
- `users` — 账号主表
- `refresh_tokens` — refresh token 登记表

关键字段：
- `users.email`（唯一，小写存储）
- `users.password_hash`（BCrypt cost=12）
- `refresh_tokens.token_hash`（SHA-256 of plain；plain 只在 cookie）

---

## 3. 包结构

```
backend/src/main/java/com/jchat/auth/
├── controller/
│   ├── AuthController.java
│   └── UserController.java
├── service/
│   ├── AuthService.java
│   ├── UserService.java
│   └── RefreshTokenService.java
├── jwt/
│   ├── JwtService.java
│   ├── JwtPrincipal.java
│   ├── JwtAuthenticationFilter.java
│   └── JwtProperties.java
├── security/
│   ├── SecurityConfig.java
│   ├── CookieUtils.java
│   └── PasswordEncoderConfig.java
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── LoginResponse.java
│   ├── UserResponse.java
│   ├── UpdateUserRequest.java
│   └── ChangePasswordRequest.java
├── entity/
│   ├── User.java
│   └── RefreshToken.java
└── repository/
    ├── UserRepository.java
    └── RefreshTokenRepository.java
```

---

## 4. Entity

### `User`

```java
@Entity
@Table(name = "users")
@SQLRestriction("deleted_at is null")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;          // 小写

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
    @UpdateTimestamp   @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;
}
```

> 不使用 Lombok（规划约定），上面只是占位。实际写成显式字段 + getter/setter 或用 Java 21 `record` 做 DTO，Entity 用普通类。

### `RefreshToken`

```java
@Entity @Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "user_agent") private String userAgent;
    @Column private String ip;
    @CreationTimestamp @Column(name = "created_at") private Instant createdAt;
    // ...
}
```

---

## 5. Service

### 5.1 `AuthService`

```java
@Service @Transactional
public class AuthService {

    public User register(RegisterRequest req, String ip) {
        rateLimit.tryAcquire("register:email:" + req.email().toLowerCase(), 1, 1);
        rateLimit.tryAcquire("register:ip:" + ip, 10, 10);   // TODO refill period 1h

        var email = req.email().toLowerCase().trim();
        if (users.existsByEmail(email)) throw new ApiException(CONFLICT, "email already registered");

        validatePassword(req.password());

        var u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setDisplayName(StringUtils.hasText(req.displayName()) ? req.displayName() : email.split("@")[0]);
        return users.save(u);
    }

    public LoginResult login(LoginRequest req, String ua, String ip) {
        rateLimit.tryAcquire("login:ip:" + ip, 10, 10);
        var u = users.findByEmail(req.email().toLowerCase().trim())
            .orElseThrow(() -> new ApiException(AUTH_INVALID, "invalid credentials"));
        if (!u.isActive()) throw new ApiException(FORBIDDEN, "account deactivated");
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ApiException(AUTH_INVALID, "invalid credentials");
        }
        return issueTokens(u, ua, ip);
    }

    public LoginResult refresh(String plainRefresh, String ua, String ip) {
        var hash = Hashing.sha256(plainRefresh);
        var rt = refreshTokens.findByTokenHash(hash)
            .orElseThrow(() -> new ApiException(AUTH_REFRESH_INVALID, "invalid refresh token"));

        if (rt.getRevokedAt() != null) {
            // 盗用检测：吊销全 session
            refreshTokens.revokeAllByUserId(rt.getUserId(), Instant.now());
            throw new ApiException(AUTH_REFRESH_INVALID, "refresh token reuse detected");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(AUTH_REFRESH_INVALID, "refresh token expired");
        }

        rt.setRevokedAt(Instant.now());   // rotate
        var user = users.findById(rt.getUserId()).orElseThrow();
        return issueTokens(user, ua, ip);
    }

    public void logout(String plainRefresh) {
        if (plainRefresh != null) {
            var hash = Hashing.sha256(plainRefresh);
            refreshTokens.findByTokenHash(hash).ifPresent(rt -> rt.setRevokedAt(Instant.now()));
        }
    }

    private LoginResult issueTokens(User u, String ua, String ip) {
        var access = jwt.issueAccess(u);
        var plain = SecureRandomStrings.generate(32);       // 32 bytes base64
        var rt = new RefreshToken();
        rt.setUserId(u.getId());
        rt.setTokenHash(Hashing.sha256(plain));
        rt.setExpiresAt(Instant.now().plus(props.refreshTtl()));
        rt.setUserAgent(StringUtils.truncate(ua, 500));
        rt.setIp(ip);
        refreshTokens.save(rt);
        return new LoginResult(u, access, plain, props.accessTtl());
    }

    private void validatePassword(String pwd) {
        if (pwd == null || pwd.length() < 8) throw new ApiException(VALIDATION_FAILED, "password must be ≥ 8 chars");
        if (!pwd.matches(".*[A-Za-z].*") || !pwd.matches(".*\\d.*"))
            throw new ApiException(VALIDATION_FAILED, "password must contain letters and digits");
    }
}
```

### 5.2 `UserService`

```java
@Service @Transactional
public class UserService {
    public UserResponse me(User u) { return UserResponse.from(u); }

    public UserResponse update(User u, UpdateUserRequest req) {
        if (req.displayName() != null) u.setDisplayName(req.displayName().trim());
        if (req.avatarUrl() != null)    u.setAvatarUrl(req.avatarUrl());
        users.save(u);
        return UserResponse.from(u);
    }

    public void changePassword(User u, ChangePasswordRequest req) {
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw new ApiException(AUTH_INVALID, "current password incorrect");
        }
        AuthService.validatePassword(req.newPassword());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);
        // 安全：吊销所有 refresh
        refreshTokens.revokeAllByUserId(u.getId(), Instant.now());
    }
}
```

---

## 6. JWT

### 6.1 `JwtService`

```java
@Service
public class JwtService {
    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(props.secret()));
        this.accessTtl = props.accessTtl();
    }

    public String issueAccess(User u) {
        var now = Instant.now();
        return Jwts.builder()
            .subject(String.valueOf(u.getId()))
            .claim("email", u.getEmail())
            .claim("displayName", u.getDisplayName())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    public JwtPrincipal parse(String token) {
        try {
            var c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new JwtPrincipal(Long.parseLong(c.getSubject()), c.get("email", String.class));
        } catch (ExpiredJwtException e) {
            throw new ApiException(AUTH_EXPIRED, "token expired");
        } catch (JwtException e) {
            throw new ApiException(AUTH_INVALID, "invalid token");
        }
    }
}

public record JwtPrincipal(Long userId, String email) {}
```

### 6.2 `JwtAuthenticationFilter`

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var principal = jwt.parse(header.substring(7));
                var user = users.findById(principal.userId())
                    .orElseThrow(() -> new ApiException(AUTH_INVALID, "user not found"));
                var auth = new JwtAuthentication(principal, user, List.of());
                SecurityContextHolder.getContext().setAuthentication(auth);
                MDC.put("userId", String.valueOf(user.getId()));
            } catch (ApiException e) {
                // 翻给 entry point
                SecurityContextHolder.clearContext();
                writeError(res, e);
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
```

### 6.3 `SecurityConfig`

```java
@Configuration @EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                                 "/api/v1/auth/refresh", "/api/v1/health",
                                 "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
                .accessDeniedHandler(new JsonAccessDeniedHandler()))
            .headers(h -> h
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentSecurityPolicy(cp -> cp.policyDirectives("default-src 'self'")));
        return http.build();
    }
}
```

### 6.4 `PasswordEncoderConfig`

```java
@Configuration
public class PasswordEncoderConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
}
```

---

## 7. Cookie 工具

```java
public class CookieUtils {

    public static ResponseCookie refreshCookie(String value, Duration ttl, boolean prod) {
        var b = ResponseCookie.from("__Host-refresh", value)
            .httpOnly(true)
            .path("/")
            .sameSite(prod ? "Strict" : "Lax")
            .maxAge(ttl);
        b = prod ? b.secure(true) : b.secure(false);       // dev 非 https 允许非 secure
        return b.build();
    }

    public static ResponseCookie clearRefresh(boolean prod) {
        return refreshCookie("", Duration.ZERO, prod);
    }

    public static String readRefresh(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        return Arrays.stream(req.getCookies())
            .filter(c -> "__Host-refresh".equals(c.getName()))
            .findFirst().map(Cookie::getValue).orElse(null);
    }
}
```

`__Host-` 前缀 Cookie **必须** `Secure + Path=/` 且不得设置 `Domain`。Dev 非 https 时浏览器会拒绝 `__Host-` 前缀 → 改用 `refresh`（不带前缀）。Dev profile 用不同的 cookie 名 `refresh`，prod 用 `__Host-refresh`。

---

## 8. Controller

### `AuthController`

```java
@RestController @RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        var u = authService.register(req, http.getRemoteAddr());
        return ResponseEntity.status(201).body(UserResponse.from(u));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        var ua = http.getHeader("User-Agent");
        var result = authService.login(req, ua, http.getRemoteAddr());
        return loginResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest http) {
        var plain = CookieUtils.readRefresh(http);
        if (plain == null) throw new ApiException(AUTH_REFRESH_INVALID, "no refresh cookie");
        var result = authService.refresh(plain, http.getHeader("User-Agent"), http.getRemoteAddr());
        return loginResponse(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(CookieUtils.readRefresh(http));
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, CookieUtils.clearRefresh(isProd).toString())
            .build();
    }

    private ResponseEntity<LoginResponse> loginResponse(LoginResult r) {
        var cookie = CookieUtils.refreshCookie(r.refreshPlain(), props.refreshTtl(), isProd);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(new LoginResponse(r.accessToken(), "Bearer", r.accessTtl().toSeconds(), UserResponse.from(r.user())));
    }
}
```

### `UserController`

```java
@RestController @RequestMapping("/api/v1/auth")
public class UserController {
    @GetMapping("/me")                    public UserResponse me(@CurrentUser User u)          { ... }
    @PatchMapping("/me")                  public UserResponse patch(@CurrentUser User u, @RequestBody UpdateUserRequest r) { ... }
    @PostMapping("/change-password")      public ResponseEntity<Void> changePwd(...)           { ... }
}
```

---

## 9. DTO（记得都用 record）

```java
public record RegisterRequest(
    @Email @NotBlank String email,
    @NotBlank String password,
    String displayName
) {}

public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {}

public record UserResponse(String id, String email, String displayName, String avatarUrl, boolean emailVerified, String createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(
            String.valueOf(u.getId()),
            u.getEmail(),
            u.getDisplayName(),
            u.getAvatarUrl(),
            u.isEmailVerified(),
            u.getCreatedAt().toString()
        );
    }
}
```

---

## 10. 测试清单（M1 必备）

### 单元
- `JwtServiceTest`：签发、过期、无效签名、payload 解析
- `AuthServiceTest`：
  - register 成功
  - register 同邮箱冲突 409
  - register 密码太弱 400
  - login 密码错 401
  - login 不存在的邮箱 401
  - refresh 过期 401
  - refresh 二次使用 → 全吊销 + 401
  - logout 清 refresh
  - changePassword 吊销所有 refresh

### 集成
- `AuthControllerIT`（Testcontainers）：
  - POST /register 201 + body 正确
  - POST /login 200 + cookie 正确（`__Host-refresh` 或 dev `refresh`）
  - POST /refresh 流程
  - 带 Authorization 访问 `/me` 返回 user
  - 不带 Authorization → 401 + `AUTH_INVALID`

### 安全
- 跨用户：拿 A 的 token 访问仅属 B 的资源 → 403（由业务 service 保证，auth 只负责鉴权）
- refresh token 存 DB 的是 hash 不是明文

---

## 11. 常见陷阱

- `__Host-` 前缀 cookie 需要 `Secure + Path=/ + 无 Domain`。dev http 下不能用，所以 dev 用 `refresh`（不带前缀）。
- `SameSite=Strict` 严格到跨站跳转登录会失败。登录流程全在同源，没问题。OAuth 回调涉及跨站时切 `Lax`。
- BCrypt cost=12 在慢 CPU 上会很慢（~300ms），**这是特性不是 bug**。测试用 cost=4 加速。
- 多个 `@RestControllerAdvice` 顺序通过 `@Order` 控制。
- JWT 过期的 exception 不要被 `handleAll` 吃掉；`GlobalExceptionHandler.handleApi` 优先级要高。
