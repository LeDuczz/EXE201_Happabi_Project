package com.minduc.happabi.observability.aspect;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.observability.logging.SensitiveDataSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
@Order(20)
public class LoggingAspect {

    @Around("@annotation(com.minduc.happabi.observability.annotation.LogExecution)")
    public Object logExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getMethod().getName();

        putMethodContext(signature, joinPoint.getArgs(), className, methodName);
        try {
            Object result = joinPoint.proceed();
            MDC.put("durationMs", String.valueOf(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
            MDC.put("status", "SUCCESS");
            log.info("[METHOD] {}.{} success durationMs={}", className, methodName, MDC.get("durationMs"));
            return result;
        } catch (AppException e) {
            MDC.put("durationMs", String.valueOf(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
            MDC.put("status", "BUSINESS_FAILURE");
            MDC.put("exception", e.getClass().getSimpleName());
            MDC.put("errorCode", e.getErrorCode().name());
            log.warn("[METHOD] {}.{} business failure code={} durationMs={}", className, methodName,
                    e.getErrorCode().name(), MDC.get("durationMs"));
            throw e;
        } catch (Exception e) {
            MDC.put("durationMs", String.valueOf(Duration.ofNanos(System.nanoTime() - startedAt).toMillis()));
            MDC.put("status", "FAILURE");
            MDC.put("exception", e.getClass().getSimpleName());
            log.error("[METHOD] {}.{} failure durationMs={}", className, methodName, MDC.get("durationMs"), e);
            throw e;
        } finally {
            clearMethodContext();
        }
    }

    private void putMethodContext(MethodSignature signature, Object[] args, String className, String methodName) {
        MDC.put("eventType", "METHOD_EXECUTION");
        MDC.put("className", className);
        MDC.put("methodName", methodName);
        MDC.put("arguments", sanitizeArgs(signature, args).toString());
    }

    private Map<String, Object> sanitizeArgs(MethodSignature signature, Object[] args) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        String[] parameterNames = signature.getParameterNames();
        for (int i = 0; i < args.length; i++) {
            String name = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
            sanitized.put(name, SensitiveDataSanitizer.sanitizeNamedValue(name, args[i]));
        }
        return sanitized;
    }

    private void clearMethodContext() {
        MDC.remove("eventType");
        MDC.remove("className");
        MDC.remove("methodName");
        MDC.remove("arguments");
        MDC.remove("durationMs");
        MDC.remove("status");
        MDC.remove("exception");
        MDC.remove("errorCode");
    }
}
