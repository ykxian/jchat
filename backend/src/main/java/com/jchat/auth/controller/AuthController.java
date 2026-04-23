package com.jchat.auth.controller;

import com.jchat.auth.dto.LoginRequest;
import com.jchat.auth.dto.LoginResponse;
import com.jchat.auth.dto.RegisterRequest;
import com.jchat.auth.dto.UserResponse;
import com.jchat.auth.security.CookieUtils;
import com.jchat.auth.service.AuthService;
import com.jchat.auth.service.LoginResult;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtils cookieUtils;

    public AuthController(AuthService authService, CookieUtils cookieUtils) {
        this.authService = authService;
        this.cookieUtils = cookieUtils;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        var user = authService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(201).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginResult result = authService.login(
                request,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return loginResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest httpRequest) {
        String refreshToken = cookieUtils.readRefreshCookie(httpRequest);
        if (refreshToken == null) {
            throw new ApiException(ErrorCode.AUTH_REFRESH_INVALID, "Refresh cookie is missing");
        }

        LoginResult result = authService.refresh(
                refreshToken,
                httpRequest.getHeader(HttpHeaders.USER_AGENT),
                httpRequest.getRemoteAddr()
        );
        return loginResponse(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(cookieUtils.readRefreshCookie(httpRequest));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<LoginResponse> loginResponse(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshCookie(result.refreshToken()).toString())
                .body(new LoginResponse(
                        result.accessToken(),
                        "Bearer",
                        result.expiresIn(),
                        UserResponse.from(result.user())
                ));
    }
}
