package com.minduc.happabi.observability.aspect;

import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.audit.AuditEvent;
import com.minduc.happabi.observability.audit.AuditRecorder;
import com.minduc.happabi.observability.audit.AuditStatus;
import com.minduc.happabi.observability.logging.SensitiveDataSanitizer;
import com.minduc.happabi.observability.support.ObservationUtils;
import com.minduc.happabi.observability.support.RequestContextUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Order(40)
public class AuditAspect {

    private final AuditRecorder auditRecorder;

    @Around("@annotation(auditAction)")
    public Object auditAction(ProceedingJoinPoint joinPoint,
                              AuditAction auditAction) throws Throwable {
        return audit(joinPoint, auditAction.action(), auditAction.resourceType());
    }

    private Object audit(ProceedingJoinPoint joinPoint, String action, String resourceType) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            record(joinPoint, result, action, resourceType, AuditStatus.SUCCESS, null);
            return result;
        } catch (Exception e) {
            record(joinPoint, null, action, resourceType, AuditStatus.FAILURE, ObservationUtils.failureReason(e));
            throw e;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, Object result, String action, String resourceType,
                        AuditStatus status, String reason) {
        auditRecorder.record(new AuditEvent(
                action,
                actorId(),
                actorRole(),
                resourceType,
                targetResourceId(joinPoint.getArgs(), result),
                status.name(),
                reason,
                RequestContextUtil.getClientIp(),
                RequestContextUtil.getUserAgent(),
                MDC.get("correlationId"),
                metadata(joinPoint)));
    }

    private Map<String, String> metadata(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("class", signature.getDeclaringType().getSimpleName());
        metadata.put("method", signature.getMethod().getName());

        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < args.length; i++) {
            String name = parameterNames != null && i < parameterNames.length ? parameterNames[i] : "arg" + i;
            Object sanitized = SensitiveDataSanitizer.sanitizeNamedValue(name, args[i]);
            metadata.put(name, String.valueOf(sanitized));
        }
        return metadata;
    }

    private String actorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return auth.getName();
    }

    private String actorRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .findFirst()
                .orElse(null);
    }

    private String targetResourceId(Object[] args, Object result) {
        return idFrom(ObservationUtils.unwrapResponse(result))
                .or(() -> Arrays.stream(args).map(this::idFrom).filter(Optional::isPresent).map(Optional::get).findFirst())
                .orElse(null);
    }

    private Optional<String> idFrom(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            Method method = value.getClass().getMethod("getId");
            Object id = method.invoke(value);
            return Optional.ofNullable(id).map(Object::toString);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }
}
