package com.minduc.happabi.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Lua script cho Sliding Window Log (sử dụng Sorted Set - ZSET).
     * ARGV[1]: now (mili-giây hiện tại)
     * ARGV[2]: window_millis (kích thước window tính bằng mili-giây)
     * ARGV[3]: member (định danh unique cho request, vd: now-uuid)
     * ARGV[4]: window_seconds (kích thước window tính bằng giây để set TTL)
     */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT;

    static {
        SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>();
        SLIDING_WINDOW_SCRIPT.setResultType(Long.class);
        SLIDING_WINDOW_SCRIPT.setScriptText(
                "local key = KEYS[1] " +
                "local now = tonumber(ARGV[1]) " +
                "local windowStart = now - tonumber(ARGV[2]) " +
                "local member = ARGV[3] " +
                "local windowSeconds = tonumber(ARGV[4]) " +
                
                // 1. Xóa các request cũ nằm ngoài thời gian window hiện tại
                "redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart) " +
                
                // 2. Lấy số lượng request hiện tại trong window
                "local count = redis.call('ZCARD', key) " +
                
                // 3. Nếu chưa vượt quá giới hạn thì mới add request mới vào để tiết kiệm bộ nhớ (Sliding Window Counter optimization)
                // Lưu ý: pass limit vào ARGV[5] nếu muốn tối ưu, nhưng ở đây ta add luôn để log chính xác (Sliding Window Log)
                "redis.call('ZADD', key, now, member) " +
                
                // Đếm lại sau khi add
                "count = count + 1 " +
                
                // 4. Set TTL cho toàn bộ ZSET bằng độ dài window để tự dọn dẹp bộ nhớ nếu không có request mới
                "redis.call('EXPIRE', key, windowSeconds) " +
                
                "return count"
        );
    }

    /**
     * Kiểm tra xem request có bị rate limit không.
     *
     * @param endpoint   tên endpoint ngắn gọn (VD: "login", "register", "resend-otp")
     * @param identifier IP address hoặc phone number
     * @param maxRequests số lần tối đa được phép trong window
     * @param window      độ dài time window
     * @return {@code true} nếu bị rate limit (đã vượt ngưỡng), {@code false} nếu còn OK
     */
    public boolean isRateLimited(String endpoint, String identifier, int maxRequests, Duration window) {
        String key = "rate:" + endpoint + ":" + identifier;
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        long windowSeconds = window.getSeconds();
        // member cần unique để không bị ghi đè trong ZSET
        String member = now + "-" + UUID.randomUUID().toString();

        try {
            Long count = redisTemplate.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of(key),
                    String.valueOf(now),
                    String.valueOf(windowMillis),
                    member,
                    String.valueOf(windowSeconds)
            );

            if (count == null) {
                // Redis không phản hồi → fail-open để không block user
                log.warn("[RateLimit] Redis unavailable, fail-open for key={}", key);
                return false;
            }

            boolean limited = count > maxRequests;
            if (limited) {
                log.warn("[RateLimit] BLOCKED endpoint={} identifier={} count={}/{}", endpoint, identifier, count, maxRequests);
            }
            return limited;

        } catch (Exception e) {
            // Fail-open: nếu Redis lỗi, không block user
            log.error("[RateLimit] Error checking rate limit for key={}: {}", key, e.getMessage());
            return false;
        }
    }

    public long getRetryAfterSeconds(String endpoint, String identifier) {
        String key = "rate:" + endpoint + ":" + identifier;
        try {
            // Lấy request cũ nhất trong ZSET
            java.util.Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> oldest = 
                    redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
            
            if (oldest != null && !oldest.isEmpty()) {
                Double score = oldest.iterator().next().getScore();
                if (score != null) {
                    long oldestTimeMillis = score.longValue();
                    long now = System.currentTimeMillis();
                    // Lấy TTL tổng của key để biết window size, hoặc có thể lấy thẳng từ config. 
                    // Tạm tính đơn giản: TTL của key = lúc nó reset toàn bộ, nhưng trong Sliding Window, 
                    // mỗi request cũ rụng đi sẽ mở ra 1 slot mới. 
                    // Slot mới sẽ có vào lúc: oldestTimeMillis + windowMillis - now
                    Long ttlSeconds = redisTemplate.getExpire(key);
                    if (ttlSeconds != null && ttlSeconds > 0) {
                        return ttlSeconds; // Return max TTL for simplicity and safety
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get retry-after from Redis", e);
        }
        return -1;
    }
}
