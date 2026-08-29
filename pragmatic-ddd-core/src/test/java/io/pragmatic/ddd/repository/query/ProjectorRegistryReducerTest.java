package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 ProjectorRegistry 裁剪器的登记、解析、反查与冲突校验。
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

        private String detail() {
            return detail;
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

    /** 另一来源的裁剪器：用于验证同一子投影多来源时的冲突校验。 */
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

    @Test
    void registerAndResolve_reducerBySourceAndProjection() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SummaryReducer reducer = new SummaryReducer();
        registry.register(reducer);

        assertThat(registry.getReducer(FullProjection.class, SummaryProjection.class)).isSameAs(reducer);
    }

    @Test
    void sourceTypeOf_returnsRegisteredSource() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new SummaryReducer());

        assertThat(registry.sourceTypeOf(SummaryProjection.class)).isEqualTo(FullProjection.class);
    }

    @Test
    void sourceTypeOf_unregistered_returnsNull() {
        ProjectorRegistry registry = new ProjectorRegistry();

        assertThat(registry.sourceTypeOf(SummaryProjection.class)).isNull();
    }

    @Test
    void getReducer_unregistered_throwsNotFound() {
        ProjectorRegistry registry = new ProjectorRegistry();

        assertThatThrownBy(() -> registry.getReducer(FullProjection.class, SummaryProjection.class))
                .isInstanceOf(ProjectionReducerNotFoundException.class)
                .hasMessageContaining(SummaryProjection.class.getName());
    }

    @Test
    void register_sameSubProjectionFromDifferentSources_throwsConflict() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new SummaryReducer());

        assertThatThrownBy(() -> registerAnotherSummaryReducer(registry))
                .isInstanceOf(ProjectionReducerConflictException.class)
                .hasMessageContaining(AnotherFullProjection.class.getName())
                .hasMessageContaining(FullProjection.class.getName());
    }

    @Test
    void register_sameReducerTwice_isIdempotent() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SummaryReducer reducer = new SummaryReducer();
        registry.register(reducer);
        registry.register(reducer);

        assertThat(registry.getReducer(FullProjection.class, SummaryProjection.class)).isSameAs(reducer);
    }

    @Test
    void markSourceProjection_andIsSourceProjection() {
        ProjectorRegistry registry = new ProjectorRegistry();

        assertThat(registry.isSourceProjection(FullProjection.class)).isFalse();

        registry.markSourceProjection(FullProjection.class);

        assertThat(registry.isSourceProjection(FullProjection.class)).isTrue();
        assertThat(registry.isSourceProjection(SummaryProjection.class)).isFalse();
    }

    @Test
    void reduce_appliesFieldTrimmingAndLevelPromotion() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new SummaryReducer());

        IProjectionReducer<FullProjection, SummaryProjection> reducer =
                registry.getReducer(FullProjection.class, SummaryProjection.class);
        SummaryProjection summary = reducer.reduce(new FullProjection(1L, "明细内容", "张三"));

        // 层级提升：源为嵌套语义的 nestedName，裁剪后成为顶层 name
        assertThat(summary.name()).isEqualTo("张三");
        assertThat(summary.id()).isEqualTo(1L);
        // 字段裁剪：detail 不进入子投影（子投影无该字段）
    }

    @Test
    void reduce_nullSource_returnsNull() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new SummaryReducer());

        IProjectionReducer<FullProjection, SummaryProjection> reducer =
                registry.getReducer(FullProjection.class, SummaryProjection.class);

        assertThat(reducer.reduce(null)).isNull();
    }

    /**
     * 单独成方法以隔离泛型：{@code AnotherSummaryReducer} 的源类型与
     * {@code SummaryReducer} 不同，需在独立调用点表达。
     */
    private void registerAnotherSummaryReducer(ProjectorRegistry registry) {
        registry.register(new AnotherSummaryReducer());
    }
}
