package com.minduc.happabi.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minduc.happabi.common.base.ErrorResponse;
import com.minduc.happabi.exception.code.AuthErrorCode;
import com.minduc.happabi.service.auth.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class TokenBlacklistFilter extends OncePerRequestFilter {

    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (tokenBlacklistService.isBlacklisted(token)) {
                log.warn("[Blacklist] Rejected blacklisted token for {} {}",
                        request.getMethod(), request.getRequestURI());
                sendTokenRevokedResponse(response, request);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendTokenRevokedResponse(HttpServletResponse response,
                                          HttpServletRequest request) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse body = ErrorResponse.of(
                AuthErrorCode.TOKEN_EXPIRED,
                request.getRequestURI(),
                "Token has been revoked. Please log in again.");

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
