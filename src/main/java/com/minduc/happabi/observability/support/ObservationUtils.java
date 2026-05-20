package com.minduc.happabi.observability.support;

import com.minduc.happabi.common.base.BaseResponse;
import com.minduc.happabi.dto.response.auth.AuthResponse;
import com.minduc.happabi.dto.response.user.UserProfileResponse;
import com.minduc.happabi.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Optional;

@UtilityClass
public class ObservationUtils {

    public Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    public String correlationId(HttpServletRequest request) {
        Object correlationId = request.getAttribute("correlationId");
        if (correlationId != null) {
            return correlationId.toString();
        }
        String correlationHeader = request.getHeader("X-Correlation-Id");
        if (correlationHeader != null && !correlationHeader.isBlank()) {
            return correlationHeader;
        }
        return null;
    }

    public String failureReason(Throwable throwable) {
        if (throwable instanceof AppException appException) {
            return appException.getErrorCode().name();
        }
        return throwable.getClass().getSimpleName();
    }

    public Object unwrapResponse(Object result) {
        Object body = result;
        if (body instanceof ResponseEntity<?> responseEntity) {
            body = responseEntity.getBody();
        }
        if (body instanceof BaseResponse<?> baseResponse) {
            body = baseResponse.getData();
        }
        return body;
    }

    public Optional<AuthResponse> authResponse(Object result) {
        Object body = unwrapResponse(result);
        if (body instanceof AuthResponse authResponse) {
            return Optional.of(authResponse);
        }
        return Optional.empty();
    }

    public Optional<UserProfileResponse> userProfile(Object result) {
        return authResponse(result).map(AuthResponse::getUser);
    }

    public Optional<Object> firstArgBySimpleName(Object[] args, String simpleName) {
        return java.util.Arrays.stream(args)
                .filter(arg -> arg != null && arg.getClass().getSimpleName().equals(simpleName))
                .findFirst();
    }

    public Optional<String> readProperty(Object target, String propertyName) {
        if (target == null) {
            return Optional.empty();
        }
        String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            Method method = target.getClass().getMethod(getterName);
            Object value = method.invoke(target);
            return Optional.ofNullable(value).map(Object::toString);
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }
}
