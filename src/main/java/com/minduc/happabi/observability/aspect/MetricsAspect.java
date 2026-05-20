package com.minduc.happabi.observability.aspect;

import com.minduc.happabi.observability.annotation.TimedAction;
import com.minduc.happabi.observability.support.ObservationUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Order(30)
public class MetricsAspect {

    private final MeterRegistry meterRegistry;

    @Around("@annotation(timedAction)")
    public Object timeAction(ProceedingJoinPoint joinPoint, TimedAction timedAction) throws Throwable {
        return record(joinPoint, timedAction.value(), true);
    }

    private Object record(ProceedingJoinPoint joinPoint, String action, boolean recordDuration) throws Throwable {
        long startedAt = System.nanoTime();
        Map<String, String> tags = baseTags(joinPoint.getArgs());
        tags.put("action", action);

        try {
            Object result = joinPoint.proceed();
            tags.put("status", "success");
            tags.put("exception", "none");
            enrichSuccessTags(tags, result);
            return result;
        } catch (Exception e) {
            tags.put("status", "failure");
            tags.put("exception", e.getClass().getSimpleName());
            throw e;
        } finally {
            meterRegistry.counter("app_action_total", toTags(tags)).increment();
            if (recordDuration) {
                meterRegistry.timer("app_action_duration_seconds", Tags.of("action", action))
                        .record(Duration.ofNanos(System.nanoTime() - startedAt));
            }
        }
    }

    private Map<String, String> baseTags(Object[] args) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("role", "none");
        tags.put("provider", "none");

        ObservationUtils.firstArgBySimpleName(args, "RegisterRequest")
                .flatMap(arg -> ObservationUtils.readProperty(arg, "role"))
                .ifPresent(role -> tags.put("role", role));

        ObservationUtils.firstArgBySimpleName(args, "LoginRequest")
                .flatMap(arg -> ObservationUtils.readProperty(arg, "portalRole"))
                .ifPresent(role -> {
                    tags.put("role", role);
                    tags.put("provider", "LOCAL");
                });

        ObservationUtils.firstArgBySimpleName(args, "SocialSyncRequest")
                .flatMap(arg -> ObservationUtils.readProperty(arg, "provider"))
                .ifPresent(provider -> tags.put("provider", provider));

        return tags;
    }

    private void enrichSuccessTags(Map<String, String> tags, Object result) {
        ObservationUtils.userProfile(result).ifPresent(user -> {
            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                tags.putIfAbsent("role", user.getRoles().get(0).name());
            }
            if (user.getLinkedProviders() != null && !user.getLinkedProviders().isEmpty()) {
                tags.putIfAbsent("provider", String.join(",", user.getLinkedProviders()));
            }
        });
    }

    private Tags toTags(Map<String, String> tags) {
        Tags result = Tags.empty();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                result = result.and(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
