package com.minduc.happabi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.exception.code.CommonErrorCode;
import com.minduc.happabi.common.base.ErrorResponse;
import com.minduc.happabi.service.metrics.AuditLogService;
import com.minduc.happabi.service.metrics.AuthMetricsService;
import com.minduc.happabi.service.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class GlobalIpRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final AuthMetricsService authMetrics;
    private final AuditLogService auditLog;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String ip = NetworkUtils.resolveClientIp(request);
        String key = "rate:global:ip:" + ip;

        RateLimitService.TokenBucketResult result = rateLimitService.tryConsumeGlobal(key);

        if (result.remaining() >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        }

        if (!result.allowed()) {
            authMetrics.recordRateLimitBlocked("global", "global");
            auditLog.logRateLimitBlocked("global", "global", ip);
            sendBlockedResponse(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendBlockedResponse(HttpServletResponse response,
                                     HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-RateLimit-Remaining", "0");

        ErrorResponse errorResponse = ErrorResponse.of(
                CommonErrorCode.RATE_LIMITED,
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
