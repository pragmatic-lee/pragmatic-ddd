package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.projection.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证裁剪器随源走：源按目标子投影类型从自身注册的 reducers 中定位，多源各持自己的 reducer。
 *
 * @author wizard-lee
 */
class ProjectorRegistryReducerTest {

    /** 索引级全量投影：对齐某物理存储索引的文档形状。 */
    private static final class FullProjection implements IAggregateProjection {

        private final Long id;
        private final String nestedName;

        private FullProjection(Long id, String nestedName) {
            this.id = id;
            this.nestedName = nestedName;
        }

        private Long id() {
            return id;
        }

        private String nestedName() {
            return nestedName;
        }
    }

    /** 业务子投影：字段裁剪 + 层级提升后的结果。 */
    private static final class SummaryProjection implements IAggregateProjection {

        private Long id;
        private String name;

        private Long id() {
            return id;
        }

        private String name() {
            return name;
        }
    }

    /** 全量 → 概要：裁掉 detail，并把 nestedName 提升为顶层 name。 */
    private static final class SummaryReducer implements IReducer<FullProjection, SummaryProjection> {

        @Override
        public Class<SummaryProjection> projectionType() {
            return SummaryProjection.class;
        }

        @Override
        public SummaryProjection reduce(FullProjection source) {
            if (source == null) {
                return null;
            }
            SummaryProjection summary = new SummaryProjection();
            summary.id = source.id();
            summary.name = source.nestedName();
            return summary;
        }
    }

    /** 承载 FullProjection 裁剪器的源。 */
    private static class FullSource extends AbstractProjectionSource<StubAggregate, Long, FullProjection> {

        private FullSource(ProjectionSource source) {
            super(source, StubAggregate.class, FullProjection.class,
                    new StubProjector<>(FullProjection.class), List.of(new SummaryReducer()));
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
        }

        @Override
        public void purge(Object aggregateId) {
        }

        @Override
        public long readVersion(Long aggregateId) {
            return 0L;
        }

        @Override
        public void rebuild(Long aggregateId) {
        }
    }

    @Test
    void getReducer_locatesBySubProjectionType() {
        FullSource source = new FullSource(ProjectionSource.of("es:full"));

        assertThat(source.getReducer(SummaryProjection.class)).isInstanceOf(SummaryReducer.class);
    }

    @Test
    void getReducer_unregisteredSubProjection_returnsNull() {
        FullSource source = new FullSource(ProjectionSource.of("es:full"));

        assertThat(source.getReducer(UnregisteredProjection.class)).isNull();
    }

    @Test
    void reduce_appliesFieldTrimmingAndLevelPromotion() {
        FullSource source = new FullSource(ProjectionSource.of("es:full"));

        IReducer<FullProjection, SummaryProjection> reducer = source.getReducer(SummaryProjection.class);
        SummaryProjection summary = reducer.reduce(new FullProjection(1L, "张三"));

        assertThat(summary.name()).isEqualTo("张三");
        assertThat(summary.id()).isEqualTo(1L);
    }

    @Test
    void reduce_nullSource_returnsNull() {
        FullSource source = new FullSource(ProjectionSource.of("es:full"));

        assertThat(source.getReducer(SummaryProjection.class).reduce(null)).isNull();
    }

    /** 未在任何源注册的子投影类型。 */
    private static final class UnregisteredProjection implements IAggregateProjection {
    }
}
