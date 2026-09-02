package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubProjection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AbstractAggregateProjector 投影类型暴露与聚合到投影的转换。
 *
 * @author wizard-lee
 */
class AbstractAggregateProjectorTest {

    /** 有真实投影逻辑的具体子类：聚合 → 投影。 */
    private static final class MappingProjector extends AbstractAggregateProjector<StubAggregate, StubProjection> {

        private MappingProjector() {
            super(StubProjection.class);
        }

        @Override
        public StubProjection project(StubAggregate aggregateRoot) {
            if (aggregateRoot == null) {
                return null;
            }
            return new StubProjection(1L, aggregateRoot.name());
        }
    }

    @Test
    void projectionType_exposedByConstructor() {
        MappingProjector projector = new MappingProjector();
        assertThat(projector.projectionType()).isEqualTo(StubProjection.class);
    }

    @Test
    void project_mapsAggregateToProjection() {
        MappingProjector projector = new MappingProjector();
        StubAggregate aggregate = new StubAggregate();
        aggregate.setName("order-1");
        StubProjection projection = projector.project(aggregate);
        assertThat(projection).isNotNull();
        assertThat(projection.id()).isEqualTo(1L);
        assertThat(projection.name()).isEqualTo("order-1");
    }

    @Test
    void project_nullAggregate_returnsNull() {
        MappingProjector projector = new MappingProjector();
        assertThat(projector.project(null)).isNull();
    }
}
