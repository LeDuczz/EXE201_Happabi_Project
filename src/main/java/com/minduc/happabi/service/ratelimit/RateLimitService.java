package com.minduc.happabi.service.ratelimit;

import com.minduc.happabi.config.ratelimit.RateLimitProperties;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties properties;
    private final MetricsRecorder metricsRecorder;

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
    private TokenBucketResult tryConsumeWithConfig(String key, int capacity,
                                                   int refillTokens, int refillSeconds) {
        long nowMs = System.currentTimeMillis();
        try {
            List<Long> result = (List<Long>) stringRedisTemplate.execute(
                    tokenBucketScript,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(refillTokens),
                    String.valueOf(refillSeconds),
                    String.valueOf(nowMs)
            );

            if (result == null || result.size() < 2) {
                record("fail_open", "bad_result");
                log.warn("[RateLimit] Redis unavailable or bad result, fail-open for key={}", key);
                return new TokenBucketResult(true, -1);
            }

            boolean allowed = result.get(0) == 1L;
            long remaining = result.get(1);

            if (!allowed) {
                record("blocked", "limit_exceeded");
                log.warn("[RateLimit] BLOCKED key={} remaining={}", key, remaining);
            } else {
                record("allowed", "none");
            }
            return new TokenBucketResult(allowed, remaining);

        } catch (Exception e) {
            record("fail_open", "redis_error");
            log.error("[RateLimit] Lua execution error for key={}: {}", key, e.getMessage());
            return new TokenBucketResult(true, -1);
        }
    }

    private void record(String result, String reason) {
        metricsRecorder.increment("happabi.rate_limit.operations", Map.of(
                "result", result,
                "reason", reason
        ));
    }
}
