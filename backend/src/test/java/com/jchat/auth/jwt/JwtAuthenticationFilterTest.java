package com.jchat.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jchat.auth.entity.User;
import com.jchat.auth.repository.UserRepository;
import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtService = Mockito.mock(JwtService.class);
        userRepository = Mockito.mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(jwtService, userRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationForValidBearerToken() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        JwtPrincipal principal = new JwtPrincipal(1L, "alice@example.com", "Alice");
        User user = new User();
        user.setId(1L);
        user.setActive(true);

        when(jwtService.parseAccessToken("valid-token")).thenReturn(principal);
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(principal, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void writesUnauthorizedErrorForInvalidBearerToken() throws Exception {
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.parseAccessToken("invalid-token"))
                .thenThrow(new ApiException(ErrorCode.AUTH_INVALID, "Invalid access token"));

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(401, response.getStatus());
        assertEquals("AUTH_INVALID", new ObjectMapper().readTree(response.getContentAsString()).get("code").asText());
    }
}
