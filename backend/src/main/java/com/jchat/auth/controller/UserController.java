package com.jchat.auth.controller;

import com.jchat.auth.dto.UserResponse;
import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.auth.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        return userService.me(principal);
    }
}
