package com.jchat.auth.service;

import com.jchat.auth.dto.UserResponse;
import com.jchat.auth.jwt.JwtPrincipal;
import com.jchat.auth.repository.UserRepository;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse me(JwtPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.AUTH_INVALID, "Authentication required");
        }

        var user = userRepository.findByIdAndDeletedAtIsNull(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Authenticated user not found"));
        return UserResponse.from(user);
    }
}
