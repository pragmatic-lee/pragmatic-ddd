package io.pragmatic.ddd.application;

/**
 * 字段解析器契约：从 Command DTO + 实体中计算实体字段值，每个需计算的字段对应一个，多场景复用。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface FieldResolver<C, E, R> {

    /** 从 Command DTO + 实体计算出该字段的值。 */
    R resolve(C command, E entity);

    /** 创建场景便捷方法：实体尚不存在，entity 置为 null。 */
    default R resolve(C command) {
        return resolve(command, null);
    }
}
