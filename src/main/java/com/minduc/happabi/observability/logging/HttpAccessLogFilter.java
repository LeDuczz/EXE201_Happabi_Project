package com.minduc.happabi.observability.logging;

import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import com.minduc.happabi.observability.support.ObservationUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private final MetricsRecorder metricsRecorder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long durationNanos = System.nanoTime() - startedAt;
            String pattern = pathPattern(request);
            Map<String, String> tags = tags(request, response, pattern, failure);
            metricsRecorder.increment("happabi.http.requests", tags);
            metricsRecorder.recordDuration("happabi.http.requests.duration", Duration.ofNanos(durationNanos), tags);
            if (shouldWriteAccessLog(request, pattern, response, failure)) {
                logAccess(request, response, pattern, durationNanos, failure);
            }
        }
    }

    private Map<String, String> tags(HttpServletRequest request, HttpServletResponse response,
                                     String pattern, Throwable failure) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("method", request.getMethod());
        tags.put("path", pattern);
        tags.put("status", String.valueOf(response.getStatus()));
        tags.put("outcome", failure == null && response.getStatus() < 500 ? "success" : "failure");
        if (failure != null) {
            tags.put("reason", failure.getClass().getSimpleName());
        }
        return tags;
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response, String pattern,
                           long durationNanos, Throwable failure) {
        MDC.put("eventType", "HTTP_ACCESS");
        MDC.put("method", request.getMethod());
        MDC.put("path", pattern);
        MDC.put("status", String.valueOf(response.getStatus()));
        MDC.put("durationMs", String.valueOf(Duration.ofNanos(durationNanos).toMillis()));
        MDC.put("ip", NetworkUtils.resolveClientIp(request));
        MDC.put("userAgent", String.valueOf(request.getHeader("User-Agent")));
        MDC.put("correlationId", ObservationUtils.correlationId(request));
        try {
            if (failure == null && response.getStatus() < 500) {
                log.info("[HTTP] {} {} status={} durationMs={}",
                        request.getMethod(), pattern, response.getStatus(), Duration.ofNanos(durationNanos).toMillis());
            } else {
                log.warn("[HTTP] {} {} status={} durationMs={} reason={}",
                        request.getMethod(), pattern, response.getStatus(), Duration.ofNanos(durationNanos).toMillis(),
                        failure != null ? failure.getClass().getSimpleName() : "HTTP_" + response.getStatus());
            }
        } finally {
            MDC.remove("eventType");
            MDC.remove("method");
            MDC.remove("path");
            MDC.remove("status");
            MDC.remove("durationMs");
            MDC.remove("ip");
            MDC.remove("userAgent");
            MDC.remove("correlationId");
        }
    }

    private boolean shouldWriteAccessLog(HttpServletRequest request, String pattern,
                                         HttpServletResponse response, Throwable failure) {
        if (failure != null || response.getStatus() >= 500) {
            return true;
        }

        String uri = request.getRequestURI();
        return !uri.startsWith("/actuator/") && !pattern.startsWith("/actuator/");
    }

    private String pathPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern != null) {
            return pattern.toString();
        }
        return request.getRequestURI();
    }
}
