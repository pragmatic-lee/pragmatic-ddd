package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.fixture.StubProjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractAggregateProjectorTest {

    @Test
    void projectionType_exposedByConstructor() {
        StubProjector projector = new StubProjector();
        assertThat(projector.projectionType()).isEqualTo(StubProjection.class);
    }

    @Test
    void project_mapsAggregateToProjection() {
        StubProjector projector = new StubProjector();
        StubAggregate aggregate = new StubAggregate();
        aggregate.setName("order-1");
        StubProjection projection = projector.project(aggregate);
        assertThat(projection).isNotNull();
        assertThat(projection.id()).isEqualTo(1L);
        assertThat(projection.name()).isEqualTo("order-1");
    }

    @Test
    void project_nullAggregate_returnsNull() {
        StubProjector projector = new StubProjector();
        assertThat(projector.project(null)).isNull();
    }
}
