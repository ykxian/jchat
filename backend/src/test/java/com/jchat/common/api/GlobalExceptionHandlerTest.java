package com.jchat.common.api;

import com.jchat.common.web.CorrelationIdFilter;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsUnifiedErrorResponseForApiException() throws Exception {
        mockMvc.perform(get("/api/v1/test/api-error").header("X-Request-Id", "req-api-1"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Request-Id", "req-api-1"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Conflict for test"))
                .andExpect(jsonPath("$.details.reason").value("duplicate"))
                .andExpect(jsonPath("$.requestId").value("req-api-1"));
    }

    @Test
    void returnsUnifiedErrorResponseForValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.name").value("must not be blank"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @RestController
    @RequestMapping("/api/v1/test")
    public static class TestController {

        @GetMapping("/api-error")
        public void apiError() {
            throw new ApiException(ErrorCode.CONFLICT, "Conflict for test", java.util.Map.of("reason", "duplicate"));
        }

        @PostMapping("/validation")
        public void validation(@Valid @RequestBody ValidationRequest request) {
        }
    }

    public record ValidationRequest(@NotBlank String name) {
    }
}
