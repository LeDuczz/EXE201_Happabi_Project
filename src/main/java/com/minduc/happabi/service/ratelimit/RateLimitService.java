package com.minduc.happabi.service.ratelimit;

import com.minduc.happabi.config.ratelimit.RateLimitProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    // StringRedisTemplate = RedisTemplate<String, String> với StringRedisSerializer
    // → tất cả key/value truyền vào Lua đều là plain UTF-8 string
    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties properties;

    @SuppressWarnings("rawtypes")
    private DefaultRedisScript<List> tokenBucketScript;

    @PostConstruct
    public void init() {
        tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/rate_limit.lua")));
        tokenBucketScript.setResultType(List.class);
    }

    public record TokenBucketResult(boolean allowed, long remaining) {}

    public TokenBucketResult tryConsume(String key, String endpointKey) {
        RateLimitProperties.BucketConfig cfg = properties.getEndpoint(endpointKey);
        return tryConsumeWithConfig(key, cfg.getCapacity(), cfg.getRefillTokens(), cfg.getRefillSeconds());
    }

    public TokenBucketResult tryConsumeGlobal(String key) {
        RateLimitProperties.BucketConfig cfg = properties.getGlobal();
        return tryConsumeWithConfig(key, cfg.getCapacity(), cfg.getRefillTokens(), cfg.getRefillSeconds());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TokenBucketResult tryConsumeWithConfig(String key, int capacity, int refillTokens, int refillSeconds) {
        long nowMs = System.currentTimeMillis();
        try {
            // StringRedisTemplate.execute() truyền KEYS và ARGV là plain string
            // → Lua nhận đúng kiểu → tonumber(ARGV[1]) hoạt động chính xác
            List<Long> result = (List<Long>) stringRedisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillTokens),
                    String.valueOf(refillSeconds),
                    String.valueOf(nowMs)
            );

            if (result == null || result.size() < 2) {
                log.warn("[RateLimit] Redis unavailable or bad result, fail-open for key={}", key);
                return new TokenBucketResult(true, -1);
            }

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);

            if (!allowed) {
                log.warn("[RateLimit] BLOCKED key={} remaining={}", key, remaining);
            }
            return new TokenBucketResult(allowed, remaining);

        } catch (Exception e) {
            log.error("[RateLimit] Lua execution error for key={}: {}", key, e.getMessage());
            return new TokenBucketResult(true, -1); // fail-open
        }
    }
}
