package io.pragmatic.ddd.base;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * 业务规则消息注册表基类（对齐 io.pragmatic.ddd.operation.OperationRegistry）。
 * 构造时反射扫描"本子类"声明的 static MessageCode 字段并自动注册，
 * 子类只需声明常量，无需 populateMessage() / register() 模板方法。
 */
public abstract class BrokenRuleMessage {

    private final Map<String, String> messageMap = new HashMap<>();

    public BrokenRuleMessage() {
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
     * 将一组 MessageCode 注册到消息映射表，以局部码 code() 为 key、description 为 value。
     * protected final，与 OperationRegistry.register(...) 同款。
     */
    protected final void register(MessageCode... codes) {
        for (MessageCode c : codes) {
            this.messageMap.put(c.code(), c.description());
        }
    }

    /**
     * 内联便利工厂（极简场景：不想单独建消息类时）。
     * 仍各自独立 INSTANCE，按实体隔离；主路径仍是"实体消息类 extends BrokenRuleMessage + INSTANCE"。
     */
    public static BrokenRuleMessage of(MessageCode... codes) {
        return new BrokenRuleMessage() {{
            register(codes);
        }};
    }

    public String getRuleDescription(String key) {
        return this.messageMap.getOrDefault(key, "");
    }

    public BrokenRuleException createException(String key) {
        return new BrokenRuleException(key, getRuleDescription(key));
    }

    public BrokenRuleException createExceptionWithParam(String key, Object... params) {
        return new BrokenRuleException(key, String.format(getRuleDescription(key), params));
    }
}
