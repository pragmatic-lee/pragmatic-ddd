package io.pragmatic.ddd.base;

/**
 * 枚举值对象标记接口，承载业务 code（getValue）、展示名（getName）与描述（getDesc）。
 *
 * @param <T> 业务 code 类型
 * @param <K> 枚举类型
 * @author wizard-lee
 */
public interface IEnumValue<T, K extends Enum<?>> {

    /** 返回持久化 / 传输用的业务 code。 */
    T getValue();

    /** 返回展示名（label），用于下拉、日志、可视化。 */
    String getName();

    /** 返回描述，默认等同展示名。 */
    default String getDesc() { return getName(); }

    // 静态 toParse 已移除 —— 枚举反序列化统一走 EnumValueResolver
}
