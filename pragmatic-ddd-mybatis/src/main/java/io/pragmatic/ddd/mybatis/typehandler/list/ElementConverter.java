package io.pragmatic.ddd.mybatis.typehandler.list;

/**
 * 元素级类型转换钩子（可选）：反序列化后对元素做最终归一化，默认恒等转换。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface ElementConverter {

    /** 把反序列化出的单个元素转换为目标形态；返回原值即表示不转换。 */
    Object convert(Object element);

    /** 恒等转换（默认）：原样返回元素。 */
    ElementConverter IDENTITY = e -> e;
}
