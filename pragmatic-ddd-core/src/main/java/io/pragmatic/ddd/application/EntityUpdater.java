package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 实体更新器契约：从 Command DTO 计算变更字段并调用实体业务方法完成修改，与 EntityFactory 对称。
 *
 * @author wizard-lee
 */
public interface EntityUpdater<T extends AggregateRoot<?>, C> {

    /** 对已有实体应用变更。 */
    void apply(T aggregateRoot, C command);
}
