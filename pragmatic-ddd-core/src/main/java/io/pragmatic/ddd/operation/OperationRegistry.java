package io.pragmatic.ddd.operation;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 实体操作注册表基类（对应设计文档 3.2 推荐方案，替代原 {@code action.EntityAction}）。
 * <p>构造时先注册内置 {@link #NEW} / {@link #DELETE}，再反射扫描子类声明的
 * {@code static EntityOperation} 字段并自动注册，子类只需声明常量，
 * 无需 {@code registerActions()} / {@code register()} 模板方法。</p>
 */
public abstract class OperationRegistry {

    public static final EntityOperation NEW = EntityOperation.of("NEW", "新建");
    public static final EntityOperation DELETE = EntityOperation.of("DELETE", "删除");

    private final Map<String, EntityOperation> operationMap = new HashMap<>();

    public OperationRegistry() {
        this.register(NEW, DELETE);
        // 反射：扫描"本子类"声明的 static EntityOperation 字段，自动注册
        for (Field f : getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    && EntityOperation.class.isAssignableFrom(f.getType())) {
                try {
                    register((EntityOperation) f.get(null));
                } catch (IllegalAccessException ignored) {
                    // 字段访问失败静默忽略，注册继续
                }
            }
        }
    }

    /**
     * 将一组 {@link EntityOperation} 注册到操作映射表中。
     * 以操作的唯一编码（code）作为 key，操作实例作为 value，
     * 供后续通过编码快速查找对应的业务操作。
     *
     * @param ops 待注册的实体操作实例（可变参数）
     */
    protected final void register(EntityOperation... ops) {
        for (EntityOperation o : ops) {
            this.operationMap.put(o.code(), o);
        }
    }

    /**
     * 返回已注册的操作映射表（只读视图）。
     * 通过 {@link Collections#unmodifiableMap} 包装，防止外部调用方修改内部映射，
     * 保证注册表在注册完成后不可被意外篡改。
     *
     * @return 编码到实体操作的可读映射，key 为操作编码，value 为对应的 {@link EntityOperation}
     */
    Map<String, EntityOperation> operations() {
        return Collections.unmodifiableMap(this.operationMap);
    }
}
