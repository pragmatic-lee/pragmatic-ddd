package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 实体工厂契约：从 Command DTO 构建新的聚合根实例，遵循"先算后赋"原则。
 *
 * @author wizard-lee
 */
public interface EntityFactory<T extends AggregateRoot<?>, C> {

    /** 从 Command DTO 构建一个新的聚合根实例。 */
    T create(C command);
}
