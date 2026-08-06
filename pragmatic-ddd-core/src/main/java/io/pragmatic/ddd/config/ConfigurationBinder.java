package io.pragmatic.ddd.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * 类型化配置绑定工具（L2）。
 * 将某一前缀下的原始键值绑定为类型化对象（record 或带 setter 的 POJO），
 * 零依赖、纯反射，可被任意模块调用而不反向依赖 core 的具体配置类型。
 *
 * @author wizard-lee
 */
public final class ConfigurationBinder {

    private ConfigurationBinder() {
    }

    /**
     * 将 prefix 前缀下的配置绑定为 type 类型实例（严格模式：record 字段缺失视为必填）。
     * record 按其组件名 {@code {prefix}.{component}} 取值构造；
     * POJO 按 setter 属性名 {@code {prefix}.{property}} 取值注入，缺失字段保留默认值。
     *
     * @param source 配置源
     * @param prefix 键值前缀
     * @param type   目标类型（record 或带无参构造与 setter 的 POJO）
     * @param <T>    目标类型
     * @return 绑定后的实例
     */
    public static <T> T bind(IConfigurationSource source, String prefix, Class<T> type) {
        return bind(source, prefix, type, null);
    }

    /**
     * 将 prefix 前缀下的配置绑定为 type 类型实例（宽松模式）。
     * 当字段在配置中缺失时，取 defaults 同名字段作为兜底值（record 适用）；
     * POJO 缺失字段则跳过 setter、保留实例默认值。便于"部分配置覆盖默认值"。
     *
     * @param source   配置源
     * @param prefix   键值前缀
     * @param type     目标类型
     * @param defaults 兜底默认值实例（可为 null；为 null 时 record 字段缺失视为必填）
     * @param <T>      目标类型
     * @return 绑定后的实例
     */
    public static <T> T bind(IConfigurationSource source, String prefix, Class<T> type, T defaults) {
        if (type.isRecord()) {
            return bindRecord(source, prefix, type, defaults);
        }
        return bindPojo(source, prefix, type);
    }

    private static <T> T bindRecord(IConfigurationSource source, String prefix, Class<T> type, T defaults) {
        RecordComponent[] components = type.getRecordComponents();
        List<Object> args = new ArrayList<>(components.length);
        for (RecordComponent component : components) {
            String key = prefix + "." + toKebab(component.getName());
            Object fallback = defaults == null ? null : readRecordField(defaults, component);
            Object value = resolve(source, key, component.getType(), component.getName(), fallback);
            args.add(value);
        }
        try {
            Object[] argArray = args.toArray();
            return type.cast(type.getDeclaredConstructors()[0].newInstance(argArray));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new ConfigurationBindingException("绑定 record[" + type.getName() + "]失败", e);
        }
    }

    private static Object readRecordField(Object record, RecordComponent component) {
        try {
            return component.getAccessor().invoke(record);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
    }

    private static <T> T bindPojo(IConfigurationSource source, String prefix, Class<T> type) {
        try {
            T instance = type.getDeclaredConstructor().newInstance();
            Method[] methods = type.getMethods();
            for (Method method : methods) {
                if (isSetter(method)) {
                    String property = propertyName(method);
                    String key = prefix + "." + toKebab(property);
                    if (!source.contains(key)) {
                        continue;
                    }
                    Class<?> paramType = method.getParameterTypes()[0];
                    Object value = resolve(source, key, paramType, property, null);
                    if (value != null) {
                        method.invoke(instance, value);
                    }
                }
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                 | NoSuchMethodException e) {
            throw new ConfigurationBindingException("绑定 POJO[" + type.getName() + "]失败", e);
        }
    }

    private static boolean isSetter(Method method) {
        if (!method.getName().startsWith("set") || method.getParameterCount() != 1) {
            return false;
        }
        Class<?> returnType = method.getReturnType();
        return returnType == void.class || returnType.equals(method.getDeclaringClass());
    }

    private static String propertyName(Method setter) {
        String name = setter.getName().substring(3);
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * 将驼峰属性名转换为 kebab-case（小写连字符），与配置文件中短横线键风格保持一致。
     *
     * @param name 驼峰属性名
     * @return kebab-case 键名
     */
    private static String toKebab(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Object resolve(IConfigurationSource source, String key, Class<?> type, String field, Object fallback) {
        if (!source.contains(key)) {
            if (fallback != null) {
                return fallback;
            }
            throw new ConfigurationBindingException("缺少必需的配置项[" + key + "]（绑定字段：" + field + "）");
        }
        String raw = source.getString(key, null);
        Object converted = convert(raw, type);
        if (converted == null) {
            throw new ConfigurationBindingException("配置项[" + key + "]无法转换为类型[" + type.getName() + "]");
        }
        return converted;
    }

    private static Object convert(String raw, Class<?> type) {
        if (type == String.class) {
            return raw;
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(raw.trim());
        }
        if (type == Long.class || type == long.class) {
            return Long.parseLong(raw.trim());
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(raw.trim());
        }
        if (type == Double.class || type == double.class) {
            return Double.parseDouble(raw.trim());
        }
        if (type == Float.class || type == float.class) {
            return Float.parseFloat(raw.trim());
        }
        if (type.isEnum()) {
            return Enum.valueOf(type.asSubclass(Enum.class), raw.trim());
        }
        if (type == java.time.Duration.class) {
            return java.time.Duration.parse(raw.trim());
        }
        return null;
    }
}
