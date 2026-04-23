package com.jchat.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.api.ErrorResponse;
import com.jchat.common.web.RequestIds;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        response.setStatus(ErrorCode.AUTH_INVALID.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                ErrorCode.AUTH_INVALID.name(),
                "Authentication required",
                null,
                RequestIds.getCurrentRequestId()
        ));
    }
}
