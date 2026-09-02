package io.pragmatic.ddd.repository.query.projection.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.projection.AbstractAggregateProjector;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;

/**
 * 壳子投影器：project 统一返回 null，仅用于满足源构造约束。
 * 真实投影逻辑由具体业务投影器承载，本类仅供测试装配使用。
 *
 * @param <T> 聚合根类型
 * @param <P> 投影类型
 * @author wizard-lee
 */
public final class StubProjector<T extends AggregateRoot<?>, P extends IAggregateProjection>
        extends AbstractAggregateProjector<T, P> {

    public StubProjector(Class<P> projectionType) {
        super(projectionType);
    }

    public StubProjector() {
        super(null);
    }

    @Override
    public P project(T aggregateRoot) {
        return null;
    }
}
