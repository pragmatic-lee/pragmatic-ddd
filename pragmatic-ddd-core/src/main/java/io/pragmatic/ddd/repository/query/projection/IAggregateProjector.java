package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 聚合投影器：将写模型聚合根映射为读模型投影。
 * 单一职责：聚合 → 投影（不含任何存储细节），可独立单测。
 *
 * @param <T> 聚合根类型（写模型）
 * @param <P> 投影类型（读模型，通常为 sealed 投影基类，定义于同包 IAggregateProjection）
 * @author wizard-lee
 */
public interface IAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection> {

    /** 将聚合根投影为指定类型 P；聚合不满足投影条件可返回 null（由调用方决定）。 */
    P project(T aggregateRoot);

    /** 本投影器产出的投影类型，供 ProjectorRegistry 按型定位。 */
    Class<P> projectionType();
}
