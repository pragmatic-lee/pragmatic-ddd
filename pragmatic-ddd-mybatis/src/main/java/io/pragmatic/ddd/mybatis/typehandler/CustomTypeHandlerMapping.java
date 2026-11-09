package io.pragmatic.ddd.mybatis.typehandler;

import org.apache.ibatis.type.TypeHandler;

import java.util.Objects;

/**
 * 自定义 TypeHandler 声明单元：业务侧显式给出 javaType 与 handler 实例的配对，
 * 交由 {@link TypeHandlerAssembler} 在合并期完成互斥与泛型一致性校验（R11–R13）后并入唯一上下文。
 *
 * <p>仅用于框架三通道（枚举 / 值对象 / 集合列）未占用的 javaType；同键冲突在启动期 fail-fast。</p>
 *
 * <p>handler 实例被全局唯一上下文持有并注册，等价于单例：必须无状态，
 * 且不得依赖 {@code EnumValueResolver} / {@code JsonSerializer} / {@code JdbcJsonValue}
 * 等框架共享组件（框架不向自定义 handler 注入）。</p>
 *
 * @author wizard-lee
 */
public record CustomTypeHandlerMapping(Class<?> javaType, TypeHandler<?> handler) {

    public CustomTypeHandlerMapping {
        Objects.requireNonNull(javaType, "javaType 不得为空");
        Objects.requireNonNull(handler, "handler 不得为空");
    }
}
