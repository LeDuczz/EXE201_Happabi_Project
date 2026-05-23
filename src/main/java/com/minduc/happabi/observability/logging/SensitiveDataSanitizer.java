package com.minduc.happabi.observability.logging;

import lombok.experimental.UtilityClass;

import java.lang.reflect.Method;
import java.util.*;

@UtilityClass
public class SensitiveDataSanitizer {

    private static final String MASK = "****";
    private static final int MAX_DEPTH = 2;
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password",
            "token",
            "authorization",
            "otp",
            "cccd",
            "secret",
            "address",
            "birth",
            "email",
            "phone"
    );

    public Object sanitize(Object value) {
        return sanitize(value, 0, new IdentityHashMap<>());
    }

    private Object sanitize(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (value == null || isSimple(value)) {
            return value;
        }
        if (depth >= MAX_DEPTH) {
            return summary(value);
        }
        if (visited.containsKey(value)) {
            return "[cyclic:" + value.getClass().getSimpleName() + "]";
        }
        visited.put(value, Boolean.TRUE);

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, mapValue) ->
                    sanitized.put(String.valueOf(key), sanitizeNamedValue(String.valueOf(key), mapValue, depth + 1, visited)));
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .limit(20)
                    .map(item -> sanitize(item, depth + 1, visited))
                    .toList();
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "[]";
        }
        if (value.getClass().getName().startsWith("org.springframework.web.multipart")) {
            return value.getClass().getSimpleName();
        }
        if (!isSafeToReflect(value)) {
            return summary(value);
        }
        return sanitizeObject(value, depth, visited);
    }

    public Object sanitizeNamedValue(String name, Object value) {
        return sanitizeNamedValue(name, value, 0, new IdentityHashMap<>());
    }

    private Object sanitizeNamedValue(String name, Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        return isSensitiveName(name) ? MASK : sanitize(value, depth, visited);
    }

    private Map<String, Object> sanitizeObject(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", value.getClass().getSimpleName());
        for (Method method : value.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String property = propertyName(method.getName());
            if (property == null) {
                continue;
            }
            try {
                Object propertyValue = method.invoke(value);
                result.put(property, sanitizeNamedValue(property, propertyValue, depth + 1, visited));
            } catch (ReflectiveOperationException ignored) {
                // Best-effort only; logging must never break business execution.
            }
        }
        return result;
    }

    private String propertyName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }

    private boolean isSensitiveName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYWORDS.stream()
                .anyMatch(normalized::contains);
    }

    private boolean isSimple(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>;
    }

    private boolean isSafeToReflect(Object value) {
        String packageName = value.getClass().getPackageName();
        return packageName.startsWith("com.minduc.happabi.dto.request")
                || packageName.startsWith("com.minduc.happabi.dto.response")
                || packageName.startsWith("com.minduc.happabi.common.base");
    }

    private String summary(Object value) {
        return value.getClass().getSimpleName();
    }
}
