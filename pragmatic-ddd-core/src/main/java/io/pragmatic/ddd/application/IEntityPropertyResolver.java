package io.pragmatic.ddd.application;

/**
 * 实体属性解析器契约：在具体场景下从 Command DTO 与实体现状解析出实体属性值。
 *
 * @param <C> 场景 Command DTO 类型
 * @param <E> 实体类型
 * @param <R> 解析出的实体属性值类型
 * @author wizard-lee
 */
@FunctionalInterface
public interface IEntityPropertyResolver<C, E, R> {

    /** 从 Command DTO 与实体现状解析出属性值。 */
    R resolve(C command, E entity);

    /** 创建场景便捷方法：实体尚不存在，entity 置为 null。 */
    default R resolve(C command) {
        return resolve(command, null);
    }
}
