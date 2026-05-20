package com.minduc.happabi.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.common.utils.NetworkUtils;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.common.base.ErrorResponse;
import com.minduc.happabi.observability.audit.AuditEvent;
import com.minduc.happabi.observability.audit.AuditRecorder;
import com.minduc.happabi.observability.metrics.MetricsRecorder;
import com.minduc.happabi.observability.support.ObservationUtils;
import com.minduc.happabi.service.ratelimit.RateLimitService;
import com.minduc.happabi.service.ratelimit.RateLimitService.TokenBucketResult;
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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final MetricsRecorder metricsRecorder;
    private final AuditRecorder auditRecorder;

    private static final Map<String, String> ENDPOINT_KEY_MAP = Map.of(
            "/api/v1/auth/register", "register",
            "/api/v1/auth/verify-otp", "verify-otp",
            "/api/v1/auth/resend-otp", "resend-otp",
            "/api/v1/auth/login", "login",
            "/api/v1/auth/social/sync", "social-sync",
            "/api/v1/auth/refresh", "refresh",
            "/api/v1/auth/logout", "logout",
            "/api/v1/auth/forgot-password", "forgot-password",
            "/api/v1/auth/reset-password", "reset-password"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String endpointKey = ENDPOINT_KEY_MAP.get(path);

        if (endpointKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);

        boolean blocked = checkRateLimit(cachedRequest, response, path, endpointKey);
        if (blocked) return;

        // Truyền cachedRequest xuống chain — Controller đọc body từ cache, không bị mất stream
        filterChain.doFilter(cachedRequest, response);
    }

    private boolean checkRateLimit(CachedBodyHttpServletRequest request,
                                   HttpServletResponse response,
                                   String path,
                                   String endpointKey) throws IOException {
        return switch (endpointKey) {
            // Dual: Phone + IP — 1 trong 2 cạn → chặn ngay
            case "register", "resend-otp", "login", "forgot-password" ->
                    checkDual(request, response, path, endpointKey);

            // Phone only
            case "verify-otp", "reset-password" ->
                    checkPhoneOnly(request, response, path, endpointKey);

            // IP only
            case "social-sync" ->
                    checkIpOnly(request, response, path, endpointKey);

            // User ID from JWT sub (authenticated endpoints)
            case "refresh", "logout" ->
                    checkUserIdOnly(request, response, path, endpointKey);

            default -> false;
        };
    }

    private boolean checkDual(CachedBodyHttpServletRequest request,
                               HttpServletResponse response,
                               String path,
                               String endpointKey) throws IOException {
        String phone = extractPhone(request);
        String ip = NetworkUtils.resolveClientIp(request);

        if (phone != null) {
            String phoneKey = buildKey(path, "phone", phone);
            TokenBucketResult phoneResult = rateLimitService.tryConsume(phoneKey, endpointKey);
            if (!phoneResult.allowed()) {
                recordRateLimitBlocked(request, endpointKey, "phone", ip);
                sendBlockedResponse(response, phoneResult.remaining(), request);
                return true;
            }
            setRemainingHeader(response, phoneResult.remaining());
        }

        String ipKey = buildKey(path, "ip", ip);
        TokenBucketResult ipResult = rateLimitService.tryConsume(ipKey, endpointKey);
        if (!ipResult.allowed()) {
            recordRateLimitBlocked(request, endpointKey, "ip", ip);
            sendBlockedResponse(response, ipResult.remaining(), request);
            return true;
        }
        setRemainingHeader(response, ipResult.remaining());
        return false;
    }

    private boolean checkPhoneOnly(CachedBodyHttpServletRequest request,
                                   HttpServletResponse response,
                                   String path,
                                   String endpointKey) throws IOException {
        String phone = extractPhone(request);
        if (phone == null) return false;

        String key = buildKey(path, "phone", phone);
        TokenBucketResult result = rateLimitService.tryConsume(key, endpointKey);
        setRemainingHeader(response, result.remaining());
        if (!result.allowed()) {
            recordRateLimitBlocked(request, endpointKey, "phone", NetworkUtils.resolveClientIp(request));
            sendBlockedResponse(response, result.remaining(), request);
            return true;
        }
        return false;
    }

    private boolean checkIpOnly(CachedBodyHttpServletRequest request,
                                HttpServletResponse response,
                                String path,
                                String endpointKey) throws IOException {
        String ip  = NetworkUtils.resolveClientIp(request);
        String key = buildKey(path, "ip", ip);
        TokenBucketResult result = rateLimitService.tryConsume(key, endpointKey);
        setRemainingHeader(response, result.remaining());
        if (!result.allowed()) {
            recordRateLimitBlocked(request, endpointKey, "ip", ip);
            sendBlockedResponse(response, result.remaining(), request);
            return true;
        }
        return false;
    }

    private boolean checkUserIdOnly(CachedBodyHttpServletRequest request,
                                    HttpServletResponse response,
                                    String path,
                                    String endpointKey) throws IOException {
        String userId = extractUserIdFromJwt(request);
        if (userId == null) return false;

        String key = buildKey(path, "user", userId);
        TokenBucketResult result = rateLimitService.tryConsume(key, endpointKey);
        setRemainingHeader(response, result.remaining());
        if (!result.allowed()) {
            recordRateLimitBlocked(request, endpointKey, "user", NetworkUtils.resolveClientIp(request));
            sendBlockedResponse(response, result.remaining(), request);
            return true;
        }
        return false;
    }

    private String extractPhone(CachedBodyHttpServletRequest request) {
        try {
            byte[] bodyBytes = request.getCachedBody();
            if (bodyBytes == null || bodyBytes.length == 0) return null;

            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);
            JsonNode phoneNode = node.get("phone");
            if (phoneNode == null || phoneNode.isNull()) return null;

            return normalizePhone(phoneNode.asText());
        } catch (Exception e) {
            log.debug("[RateLimit] Could not parse phone from body: {}", e.getMessage());
            return null;
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String cleaned = phone.trim();
        if (cleaned.startsWith("+")) return cleaned;
        if (cleaned.startsWith("84")) return "+" + cleaned;
        if (cleaned.startsWith("0")) return "+84" + cleaned.substring(1);
        return cleaned;
    }

    private String extractUserIdFromJwt(HttpServletRequest request) {
        try {
            String auth = request.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String token   = auth.substring(7);
            String payload = token.split("\\.")[1];
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            JsonNode claims = objectMapper.readTree(decoded);
            JsonNode sub = claims.get("sub");
            return sub != null && !sub.isNull() ? sub.asText() : null;
        } catch (Exception e) {
            log.debug("[RateLimit] Could not extract sub from JWT: {}", e.getMessage());
            return null;
        }
    }

    private String buildKey(String path, String type, String value) {
        String shortPath = path.replace("/api/v1", "");
        return "rate:" + shortPath + ":" + type + ":" + NetworkUtils.redisKeyPart(value);
    }

    private void setRemainingHeader(HttpServletResponse response, long remaining) {
        if (remaining >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        }
    }

    private void sendBlockedResponse(HttpServletResponse response, long remaining,
                                     HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        ErrorResponse errorResponse = ErrorResponse.of(
                AuthErrorCode.RATE_LIMITED,
                request.getRequestURI()
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    private void recordRateLimitBlocked(HttpServletRequest request, String endpointKey, String type, String ip) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("endpoint", endpointKey);
        tags.put("type", type);
        tags.put("outcome", "blocked");
        metricsRecorder.increment("happabi.auth.rate_limit.blocked", tags);

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("endpoint", endpointKey);
        attributes.put("identifierType", type);
        auditRecorder.record(new AuditEvent(
                "AUTH_RATE_LIMITED",
                null,
                null,
                "REQUEST",
                endpointKey,
                "FAILURE",
                "RATE_LIMITED",
                ip,
                request.getHeader("User-Agent"),
                ObservationUtils.correlationId(request),
                attributes));
    }
}
