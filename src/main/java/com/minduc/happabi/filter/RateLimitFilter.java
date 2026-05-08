package com.minduc.happabi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.service.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private static final Map<String, int[]> RATE_LIMIT_CONFIG = Map.of(
            "/api/v1/auth/login",           new int[]{5,  900},  // 5 lần / 15 phút / IP
            "/api/v1/auth/register",        new int[]{3,  3600}, // 3 lần / 1 giờ / IP
            "/api/v1/auth/resend-otp",      new int[]{3,  600},  // 3 lần / 10 phút / IP
            "/api/v1/auth/verify-otp",      new int[]{5,  600},  // 5 lần / 10 phút / IP
            "/api/v1/auth/refresh",         new int[]{20, 60},   // 20 lần / 1 phút / IP
            "/api/v1/auth/social/sync",     new int[]{5,  60},   // 5 lần / 1 phút / IP
            "/api/v1/auth/forgot-password", new int[]{3,  600},  // 3 lần / 10 phút / IP (tránh spam SMS)
            "/api/v1/auth/reset-password",  new int[]{5,  600}   // 5 lần / 10 phút / IP (tránh dò OTP)
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        int[] config = RATE_LIMIT_CONFIG.get(path);

        // Endpoint không nằm trong danh sách → bỏ qua rate limiting
        if (config == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxRequests = config[0];
        Duration window = Duration.ofSeconds(config[1]);
        String identifier = resolveIdentifier(request);
        String endpoint = path.substring(path.lastIndexOf('/') + 1); // vd: "login"

        if (rateLimitService.isRateLimited(endpoint, identifier, maxRequests, window)) {
            long retryAfter = rateLimitService.getRetryAfterSeconds(endpoint, identifier);
            sendRateLimitResponse(response, retryAfter);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveIdentifier(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For có thể là "client, proxy1, proxy2" → lấy đầu tiên
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        if (retryAfterSeconds > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }

        BaseResponse<Void> body = BaseResponse.<Void>builder()
                .success(false)
                .message("Too many requests. Please slow down and try again later.")
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}