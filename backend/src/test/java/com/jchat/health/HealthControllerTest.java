package com.jchat.health;

import com.jchat.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void returnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void echoesProvidedRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "req-health-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-health-1"))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
