package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubProjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证以「源」为中心后，裁剪器的登记、解析、多源共存与歧义校验。
 *
 * @author wizard-lee
 */
class ProjectorRegistryReducerTest {

    /** 索引级全量投影：对齐某物理存储索引的文档形状。 */
    private static final class FullProjection implements IAggregateProjection {

        private final Long id;
        private final String detail;
        private final String nestedName;

        private FullProjection(Long id, String detail, String nestedName) {
            this.id = id;
            this.detail = detail;
            this.nestedName = nestedName;
        }

        private Long id() {
            return id;
        }

        private String nestedName() {
            return nestedName;
        }
    }

    /** 索引级全量投影 B：另一套索引的文档形状。 */
    private static final class AnotherFullProjection implements IAggregateProjection {
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
    private static final class SummaryReducer
            implements IProjectionReducer<FullProjection, SummaryProjection> {

        @Override
        public Class<FullProjection> sourceType() {
            return FullProjection.class;
        }

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

    /** 另一来源的裁剪器：用于验证同一子投影多来源时合法共存（而非冲突）。 */
    private static final class AnotherSummaryReducer
            implements IProjectionReducer<AnotherFullProjection, SummaryProjection> {

        @Override
        public Class<AnotherFullProjection> sourceType() {
            return AnotherFullProjection.class;
        }

        @Override
        public Class<SummaryProjection> projectionType() {
            return SummaryProjection.class;
        }

        @Override
        public SummaryProjection reduce(AnotherFullProjection source) {
            return new SummaryProjection();
        }
    }

    /** 承载 FullProjection 裁剪器的源。 */
    private static class FullSource extends AbstractProjectionSource<StubAggregate, FullProjection> {

        private FullSource(ProjectionSource source) {
            super(source, StubAggregate.class, FullProjection.class,
                    new StubProjector<>(FullProjection.class), null);
            bind(new SummaryReducer());
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
        }

        @Override
        public void purge(Object aggregateId) {
        }
    }

    /** 承载 AnotherFullProjection 裁剪器的源。 */
    private static final class AnotherFullSource extends AbstractProjectionSource<StubAggregate, AnotherFullProjection> {

        private AnotherFullSource(ProjectionSource source) {
            super(source, StubAggregate.class, AnotherFullProjection.class,
                    new StubProjector<>(AnotherFullProjection.class), null);
            bind(new AnotherSummaryReducer());
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
        }

        @Override
        public void purge(Object aggregateId) {
        }
    }

    @Test
    void registerAndResolve_reducerBySourceAndProjection() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:full");
        registry.register(new FullSource(source));

        assertThat(registry.getReducer(source, SummaryProjection.class)).isInstanceOf(SummaryReducer.class);
    }

    @Test
    void sameSubProjectionFromDifferentSources_coexists() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new FullSource(ProjectionSource.of("es:full")));
        registry.register(new AnotherFullSource(ProjectionSource.of("es:another")));

        assertThat(registry.sourcesOf(SummaryProjection.class)).hasSize(2);
    }

    @Test
    void resolveSource_ambiguousWhenMultipleSourcesAndNoDefault() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new FullSource(ProjectionSource.of("es:full")));
        registry.register(new AnotherFullSource(ProjectionSource.of("es:another")));

        assertThatThrownBy(() -> registry.resolveSource(SummaryProjection.class, null))
                .isInstanceOf(ProjectionSourceAmbiguousException.class);
    }

    @Test
    void resolveSource_specifiedSource_resolves() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource full = ProjectionSource.of("es:full");
        registry.register(new FullSource(full));
        registry.register(new AnotherFullSource(ProjectionSource.of("es:another")));

        assertThat(registry.resolveSource(SummaryProjection.class, full)).isEqualTo(full);
    }

    @Test
    void resolveSource_defaultSource_pickedWhenAmbiguous() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource full = ProjectionSource.of("es:full");
        ProjectionSource another = ProjectionSource.of("es:another");
        registry.register(new FullSource(full));
        registry.register(new AnotherFullSource(another));
        registry.registerDefaultSource(SummaryProjection.class, another);

        assertThat(registry.resolveSource(SummaryProjection.class, null)).isEqualTo(another);
    }

    @Test
    void getReducer_unregisteredSubProjection_throwsNotFound() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:full");
        registry.register(new FullSource(source));

        assertThatThrownBy(() -> registry.getReducer(source, AnotherFullProjection.class))
                .isInstanceOf(ProjectionSourceNotFoundException.class);
    }

    @Test
    void bind_sameSubProjectionTwice_throwsConflict() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:full");
        registry.register(new FullSource(source));

        assertThatThrownBy(() -> registry.register(new FullSource(source) {
            {
                bind(new AnotherSummaryReducer());
            }
        })).isInstanceOf(ProjectionSourceConflictException.class);
    }

    @Test
    void reduce_appliesFieldTrimmingAndLevelPromotion() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:full");
        registry.register(new FullSource(source));

        IProjectionReducer<FullProjection, SummaryProjection> reducer =
                registry.getReducer(source, SummaryProjection.class);
        SummaryProjection summary = reducer.reduce(new FullProjection(1L, "明细内容", "张三"));

        // 层级提升：源为嵌套语义的 nestedName，裁剪后成为顶层 name
        assertThat(summary.name()).isEqualTo("张三");
        assertThat(summary.id()).isEqualTo(1L);
    }

    @Test
    void reduce_nullSource_returnsNull() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:full");
        registry.register(new FullSource(source));

        IProjectionReducer<FullProjection, SummaryProjection> reducer =
                registry.getReducer(source, SummaryProjection.class);

        assertThat(reducer.reduce(null)).isNull();
    }
}
