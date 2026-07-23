package io.pragmatic.ddd.mybatis.typehandler.list;

/**
 * 元素级类型转换钩子（可选）：在反序列化得到元素后，对其做最终归一化。
 *
 * <p>用于"同列同名但期望类型与 JSON 内形态不一致"的兜底（如 JSON 数字 → 期望 String）。
 * 多表隔离主要靠 {@link CollectionMapping#columnLabel()}（SQL 别名）区分，本钩子仅作补充，
 * 默认实现为恒等转换（不改动元素）。
 */
@FunctionalInterface
public interface ElementConverter {

    /** 把反序列化出的单个元素转换为目标形态；返回原值即表示不转换。 */
    Object convert(Object element);

    /** 恒等转换（默认）：原样返回元素。 */
    ElementConverter IDENTITY = e -> e;
}
