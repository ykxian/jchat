package com.jchat.common.redis;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(RedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void tryAcquireSetsExpireOnFirstHit() {
        when(valueOperations.increment("tool:test:7")).thenReturn(1L);

        rateLimitService.tryAcquire("tool:test:7", 5, Duration.ofMinutes(1));

        verify(redisTemplate).expire("tool:test:7", Duration.ofMinutes(1));
    }

    @Test
    void tryAcquireThrowsWhenLimitExceeded() {
        when(valueOperations.increment("tool:test:7")).thenReturn(6L);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> rateLimitService.tryAcquire("tool:test:7", 5, Duration.ofMinutes(1))
        );

        assertEquals(ErrorCode.RATE_LIMITED, exception.getErrorCode());
        assertEquals("Rate limit exceeded", exception.getMessage());
    }
}
