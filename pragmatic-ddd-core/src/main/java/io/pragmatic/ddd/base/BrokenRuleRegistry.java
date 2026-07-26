package io.pragmatic.ddd.base;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务规则消息注册表基类（对齐 io.pragmatic.ddd.operation.OperationRegistry）。
 * 构造时反射扫描"本子类"声明的 static MessageCode 字段并自动注册，
 * 子类只需声明常量，无需 populateMessage() / register() 模板方法。
 */
public abstract class BrokenRuleRegistry {

    /** 对齐 OperationRegistry.operationMap：以局部码 code 为 key、MessageCode 为 value */
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

    /**
     * 将一组 MessageCode 注册到规则映射表，以局部码 code() 为 key。
     * protected final，与 OperationRegistry.register(...) 同款。
     */
    protected final void register(MessageCode... codes) {
        for (MessageCode c : codes) {
            this.brokenRuleMap.put(c.code(), c);
        }
    }

    /**
     * 返回已注册的规则映射表（只读视图），对齐 OperationRegistry.operations()。
     *
     * @return 编码到 MessageCode 的可读映射，key 为局部码，value 为对应的 {@link MessageCode}
     */
    Map<String, MessageCode> brokenRules() {
        return Collections.unmodifiableMap(this.brokenRuleMap);
    }

    /**
     * 内联便利工厂（极简场景：不想单独建消息类时）。
     * 仍各自独立 INSTANCE，按实体隔离；主路径仍是"实体消息类 extends BrokenRuleRegistry + INSTANCE"。
     */
    public static BrokenRuleRegistry of(MessageCode... codes) {
        return new BrokenRuleRegistry() {{
            register(codes);
        }};
    }

    public String getRuleDescription(String key) {
        MessageCode code = this.brokenRuleMap.get(key);
        return code != null ? code.description() : "";
    }

    public BrokenRuleException createException(String key) {
        return new BrokenRuleException(key, getRuleDescription(key));
    }

    public BrokenRuleException createExceptionWithParam(String key, Object... params) {
        return new BrokenRuleException(key, String.format(getRuleDescription(key), params));
    }
}
