package io.github.tavataan.nowbarcolor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/** Cache metadata, never view/item values. Missing OEM members are cached too. */
final class ReflectionCache {
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final Object MISSING = new Object();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> METHODS = new ConcurrentHashMap<>();

    private static ConcurrentHashMap<String, Object> members(
            ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, Object>> cache, Class<?> type) {
        ConcurrentHashMap<String, Object> found = cache.get(type);
        if (found != null) return found;
        ConcurrentHashMap<String, Object> fresh = new ConcurrentHashMap<>();
        found = cache.putIfAbsent(type, fresh);
        return found == null ? fresh : found;
    }

    static Field field(Class<?> type, String name) {
        ConcurrentHashMap<String, Object> map = members(FIELDS, type);
        Object value = map.get(name);
        if (value == null) {
            value = MISSING;
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    value = field;
                    break;
                } catch (NoSuchFieldException ignored) {
                } catch (SecurityException ignored) { break; }
            }
            map.putIfAbsent(name, value);
        }
        return value == MISSING ? null : (Field) value;
    }

    static Method method(Class<?> type, String name) {
        return method(type, name, NO_PARAMETERS);
    }

    static Method method(Class<?> type, String name, Class<?>... parameters) {
        String key = name;
        if (parameters.length != 0) {
            StringBuilder signature = new StringBuilder(name);
            for (Class<?> parameter : parameters) signature.append(':').append(parameter.getName());
            key = signature.toString();
        }
        ConcurrentHashMap<String, Object> map = members(METHODS, type);
        Object value = map.get(key);
        if (value == null) {
            value = MISSING;
            try {
                Method method = type.getMethod(name, parameters);
                method.setAccessible(true);
                value = method;
            } catch (ReflectiveOperationException | SecurityException ignored) {}
            map.putIfAbsent(key, value);
        }
        return value == MISSING ? null : (Method) value;
    }
}
