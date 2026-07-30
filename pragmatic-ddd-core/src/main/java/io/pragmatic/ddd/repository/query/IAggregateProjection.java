package io.pragmatic.ddd.repository.query;

/**
 * 聚合投影（读模型）标记接口。
 *
 * <p>与聚合根（写模型 {@code AggregateRoot}）严格区分。一个聚合的多种投影用
 * sealed interface 继承本接口形成封闭体系，调用方通过 pattern match 获取具体投影。</p>
 *
 * <p>仅聚合拓扑级投影需实现本接口；嵌套的子实体投影（如订单项投影）不需要实现。</p>
 *
 * @author wizard-lee
 */
public interface IAggregateProjection {
}
