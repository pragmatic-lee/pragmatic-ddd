package io.pragmatic.ddd.base;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务规则消息注册表基类。
 * 构造时反射扫描子类声明的 static MessageCode 字段并自动注册，子类只需声明常量即可。
 *
 * @author wizard-lee
 */
public abstract class BrokenRuleRegistry {

    /** 以局部码 code 为 key、MessageCode 为 value 的规则映射表。 */
    private final Map<String, MessageCode> brokenRuleMap = new HashMap<>();

    public BrokenRuleRegistry() {
        // 反射：扫描"本子类"声明的 static MessageCode 字段，自动注册
        for (Field f : getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    && MessageCode.class.isAssignableFrom(f.getType())) {
                try {
                    register((MessageCode) f.get(null));
                } catch (IllegalAccessException ignored) {
                    // 字段访问失败静默忽略，注册继续
                }
            }
        }
    }

    /** 将一组 MessageCode 以局部码 code() 为 key 注册到规则映射表。 */
    protected final void register(MessageCode... codes) {
        for (MessageCode c : codes) {
            this.brokenRuleMap.put(c.code(), c);
        }
    }

    /** 返回已注册的规则映射表（只读视图）。 */
    Map<String, MessageCode> brokenRules() {
        return Collections.unmodifiableMap(this.brokenRuleMap);
    }

    /** 内联便利工厂：以一组 MessageCode 直接构建注册表实例。 */
    public static BrokenRuleRegistry of(MessageCode... codes) {
        return new BrokenRuleRegistry() {{
            register(codes);
        }};
    }

    /** 按局部码返回规则描述，未注册时返回空串。 */
    public String getRuleDescription(String key) {
        MessageCode code = this.brokenRuleMap.get(key);
        return code != null ? code.description() : "";
    }

    /** 按局部码构造规则违反异常。 */
    public BrokenRuleException createException(String key) {
        return new BrokenRuleException(key, getRuleDescription(key));
    }

    /** 按局部码构造支持参数格式化的规则违反异常。 */
    public BrokenRuleException createExceptionWithParam(String key, Object... params) {
        return new BrokenRuleException(key, String.format(getRuleDescription(key), params));
    }
}
