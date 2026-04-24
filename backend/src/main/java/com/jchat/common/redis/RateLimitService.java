package com.jchat.common.redis;

import com.jchat.common.api.ApiException;
import com.jchat.common.api.ErrorCode;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitService(@Qualifier("appRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void tryAcquire(String key, int limit, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return;
        }
        if (count == 1L) {
            redisTemplate.expire(key, window);
        }
        if (count > limit) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Rate limit exceeded");
        }
    }
}
