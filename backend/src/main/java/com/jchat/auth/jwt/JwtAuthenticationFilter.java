package com.jchat.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.auth.repository.UserRepository;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import com.jchat.common.api.ErrorResponse;
import com.jchat.common.web.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtPrincipal principal = jwtService.parseAccessToken(authorization.substring(7));
            var user = userRepository.findByIdAndDeletedAtIsNull(principal.userId())
                    .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID, "Authenticated user not found"));
            if (!user.isActive()) {
                throw new ApiException(ErrorCode.FORBIDDEN, "Account is deactivated");
            }

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put("userId", String.valueOf(user.getId()));

            try {
                filterChain.doFilter(request, response);
            } finally {
                MDC.remove("userId");
            }
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, ex);
        }
    }

    private void writeErrorResponse(HttpServletResponse response, ApiException ex) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(ex.getErrorCode().getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ErrorResponse(
                ex.getErrorCode().name(),
                ex.getMessage(),
                ex.getDetails(),
                RequestIds.getCurrentRequestId()
        ));
    }
}
