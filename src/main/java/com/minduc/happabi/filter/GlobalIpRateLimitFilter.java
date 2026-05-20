package com.minduc.happabi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.exception.code.CommonErrorCode;
import com.minduc.happabi.common.base.ErrorResponse;
import com.minduc.happabi.observability.audit.AuditEvent;
import com.minduc.happabi.observability.audit.AuditRecorder;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import com.minduc.happabi.observability.support.ObservationUtils;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class GlobalIpRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final MetricsRecorder metricsRecorder;
    private final AuditRecorder auditRecorder;

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
            recordRateLimitBlocked(request, ip);
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

    private void recordRateLimitBlocked(HttpServletRequest request, String ip) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("endpoint", "global");
        tags.put("type", "global");
        tags.put("outcome", "blocked");
        metricsRecorder.increment("happabi.auth.rate_limit.blocked", tags);

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("endpoint", "global");
        attributes.put("identifierType", "global");
        auditRecorder.record(new AuditEvent(
                "AUTH_RATE_LIMITED",
                null,
                null,
                "REQUEST",
                "global",
                "FAILURE",
                "RATE_LIMITED",
                ip,
                request.getHeader("User-Agent"),
                ObservationUtils.correlationId(request),
                attributes));
    }
}
