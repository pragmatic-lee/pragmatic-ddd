package io.pragmatic.ddd.base;

public interface IEnumValue<T, K extends Enum<?>> {

    /** 持久化 / 传输用的业务 code。必填。 */
    T getValue();

    /** 展示名（label），用于下拉、日志、可视化。必填。 */
    String getName();

    /** 描述 / 说明（可选，默认等同展示名）。 */
    default String getDesc() { return getName(); }

    // 静态 toParse 已移除 —— 枚举反序列化统一走 EnumValueResolver
}
