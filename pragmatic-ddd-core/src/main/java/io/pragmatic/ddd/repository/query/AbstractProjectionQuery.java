package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 聚合查询抽象基类：把「按投影类型选路 / 检索 / 裁剪 / 缺省短路」的通用流程上收，
 * 各聚合的查询实现只需继承本类并注入 {@link ProjectorRegistry} 与三族条件类型即可。
 *
 * <p>查询链路统一为三跳：
 * <ol>
 *     <li>{@code resolveSource} 解析使用的源（指定源直接校验；未指定源按投影类型定位，多源取默认源）；</li>
 *     <li>目标为全量投影时短路直返（分页 / 滚动复用结果页实例）；</li>
 *     <li>否则按 (源, 子投影) 取裁剪器逐条 {@code reduce}。</li>
 * </ol>
 *
 * <p>支持两种视图：默认视图（隐式选路）与 {@link #source(ProjectionSource)} 指定源视图，
 * 以及 {@link #fallbackChain(List)} 多源回源视图。三者方法调用形状一致。</p>
 *
 * @param <ID> 聚合 ID 类型
 * @param <P> 投影体系基类型
 * @param <ONE> 单条查询条件族
 * @param <LIST> 列表查询条件族
 * @param <PAGE> 分页查询条件族
 * @author wizard-lee
 */
public abstract class AbstractProjectionQuery<ID, P extends IAggregateProjection,
        ONE extends OneQueryCriteria,
        LIST extends ListQueryCriteria,
        PAGE extends PageQueryCriteria>
        implements IAggregateQuery<ID, P, ONE, LIST, PAGE> {

    private final ProjectorRegistry registry;
    private final Class<ONE> oneType;
    private final Class<LIST> listType;
    private final Class<PAGE> pageType;

    protected AbstractProjectionQuery(
            ProjectorRegistry registry,
            Class<ONE> oneType,
            Class<LIST> listType,
            Class<PAGE> pageType) {
        this.registry = registry;
        this.oneType = oneType;
        this.listType = listType;
        this.pageType = pageType;
    }

    /** 返回绑定指定源的查询视图。 */
    @Override
    public IProjectionSourceQuery<ID, P, ONE, LIST, PAGE> source(ProjectionSource source) {
        return new SourceScopedQuery(source);
    }

    /** 暴露底层注册中心，供子类在框架三跳之外做源定位等扩展。 */
    protected ProjectorRegistry registry() {
        return registry;
    }

    /** 默认视图未绑定源，返回 null；指定源视图由 {@link #source(ProjectionSource)} 承载。 */
    @Override
    public ProjectionSource source() {
        return null;
    }

    /** 返回按回源顺序查询的视图。 */
    @Override
    public IProjectionSourceQuery<ID, P, ONE, LIST, PAGE> fallbackChain(List<ProjectionSource> sources) {
        return new FallbackChainQuery(sources);
    }

    /** 按主键查询：默认源。 */
    @Override
    public <X extends P> X queryById(ID id, Class<X> projectionType) {
        return queryById(id, null, projectionType);
    }

    /** 按批量主键查询：默认源。 */
    @Override
    public <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType) {
        return queryByIds(ids, null, projectionType);
    }

    /** 按单条件查询：默认源。 */
    @Override
    public <X extends P> X queryOne(ONE query, Class<X> projectionType) {
        return queryOne(query, null, projectionType);
    }

    /** 按列表条件查询：默认源。 */
    @Override
    public <X extends P> List<X> queryList(LIST query, Class<X> projectionType) {
        return queryList(query, null, projectionType);
    }

    /** 分页查询：默认源。 */
    @Override
    public <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, Class<X> projectionType) {
        return queryPage(query, pageRequest, null, projectionType);
    }

    /** 滚动查询：默认源。 */
    @Override
    public <X extends P> ScrollResult<X> queryScroll(PAGE query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
        return queryScroll(query, cursor, pageSize, null, projectionType);
    }

    // ===================== 指定源 / 默认源 实现 =====================

    @SuppressWarnings("unchecked")
    private <X extends P> X queryById(ID id, ProjectionSource source, Class<X> projectionType) {
        ProjectionSource resolved = registry.resolveSource(projectionType, source);
        onSourceResolved(resolved, projectionType);
        if (isFullProjection(resolved, projectionType)) {
            return cast(registry.getByIdSearcher(resolved).getById(id));
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        IAggregateProjection full = registry.getByIdSearcher(resolved).getById(id);
        return full == null ? null : reduceWith(reducer, full);
    }

    @SuppressWarnings("unchecked")
    private <X extends P> List<X> queryByIds(List<ID> ids, ProjectionSource source, Class<X> projectionType) {
        ProjectionSource resolved = registry.resolveSource(projectionType, source);
        onSourceResolved(resolved, projectionType);
        List<Object> objectIds = (List<Object>) ids;
        if (isFullProjection(resolved, projectionType)) {
            return castList(registry.getByIdSearcher(resolved).getByIds(objectIds));
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        List<? extends IAggregateProjection> fulls = registry.getByIdSearcher(resolved).getByIds(objectIds);
        return reduceAll(reducer, fulls);
    }

    @SuppressWarnings("unchecked")
    private <X extends P> X queryOne(ONE query, ProjectionSource source, Class<X> projectionType) {
        ProjectionSource resolved = registry.resolveSource(projectionType, source);
        onSourceResolved(resolved, projectionType);
        if (isFullProjection(resolved, projectionType)) {
            return cast(registry.getSearcher(resolved, oneType).search(query).stream().findFirst().orElse(null));
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        return registry.getSearcher(resolved, oneType).search(query).stream()
                .findFirst()
                .map(full -> reduceWith(reducer, full))
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private <X extends P> List<X> queryList(LIST query, ProjectionSource source, Class<X> projectionType) {
        ProjectionSource resolved = registry.resolveSource(projectionType, source);
        onSourceResolved(resolved, projectionType);
        if (isFullProjection(resolved, projectionType)) {
            return castList(registry.getSearcher(resolved, listType).search(query));
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        return reduceAll(reducer, registry.getSearcher(resolved, listType).search(query));
    }

    @SuppressWarnings("unchecked")
    private <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, ProjectionSource source, Class<X> projectionType) {
        ProjectionSource resolved = registry.resolveSource(projectionType, source);
        onSourceResolved(resolved, projectionType);
        PageResult<? extends IAggregateProjection> page = registry.getPagedSearcher(resolved, pageType).searchPage(query, pageRequest);
        if (isFullProjection(resolved, projectionType)) {
            return PageResult.of(castList(page.data()), page.totalCount(), page.request());
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        return PageResult.of(reduceAll(reducer, page.data()), page.totalCount(), page.request());
    }

    @SuppressWarnings("unchecked")
    private <X extends P> ScrollResult<X> queryScroll(PAGE query, ScrollPosition cursor, int pageSize, ProjectionSource source, Class<X> projectionType) {
        ProjectionSource resolved = registry.resolveSource(projectionType, source);
        onSourceResolved(resolved, projectionType);
        ScrollResult<? extends IAggregateProjection> scroll = registry.getPagedSearcher(resolved, pageType).searchScroll(query, cursor, pageSize);
        if (isFullProjection(resolved, projectionType)) {
            return ScrollResult.of(castList(scroll.data()), scroll.nextCursor());
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        return ScrollResult.of(reduceAll(reducer, scroll.data()), scroll.nextCursor());
    }

    private boolean isFullProjection(ProjectionSource resolved, Class<?> projectionType) {
        return registry.getSource(resolved).projectionType().equals(projectionType);
    }

    @SuppressWarnings("unchecked")
    private <X extends P> X reduceWith(IProjectionReducer<?, X> reducer, IAggregateProjection full) {
        return ((IProjectionReducer<IAggregateProjection, X>) reducer).reduce(full);
    }

    private <X extends P> List<X> reduceAll(IProjectionReducer<?, X> reducer, List<? extends IAggregateProjection> fulls) {
        List<X> result = new ArrayList<>(fulls.size());
        for (IAggregateProjection full : fulls) {
            if (full != null) {
                result.add(reduceWith(reducer, full));
            }
        }
        return result;
    }

    // ===================== 回源链 =====================

    /** 按回源顺序查询：前源未取到结果时自动推进下一源。 */
    private final class FallbackChainQuery implements IProjectionSourceQuery<ID, P, ONE, LIST, PAGE> {

        private final List<ProjectionSource> sources;

        private FallbackChainQuery(List<ProjectionSource> sources) {
            this.sources = List.copyOf(sources);
        }

        @Override
        public ProjectionSource source() {
            return sources.isEmpty() ? null : sources.get(0);
        }

        @Override
        public <X extends P> X queryById(ID id, Class<X> projectionType) {
            for (ProjectionSource src : sources) {
                X result = AbstractProjectionQuery.this.queryById(id, src, projectionType);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        @Override
        public <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType) {
            for (ProjectionSource src : sources) {
                List<X> hit = AbstractProjectionQuery.this.queryByIds(ids, src, projectionType);
                if (hit != null && !hit.isEmpty()) {
                    return hit;
                }
            }
            return List.of();
        }

        @Override
        public <X extends P> X queryOne(ONE query, Class<X> projectionType) {
            for (ProjectionSource src : sources) {
                X result = AbstractProjectionQuery.this.queryOne(query, src, projectionType);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        @Override
        public <X extends P> List<X> queryList(LIST query, Class<X> projectionType) {
            for (ProjectionSource src : sources) {
                List<X> result = AbstractProjectionQuery.this.queryList(query, src, projectionType);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
            return List.of();
        }

        @Override
        public <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, Class<X> projectionType) {
            ProjectionSource resolved = firstSupporting(this::pageSourceSupported, projectionType);
            return AbstractProjectionQuery.this.queryPage(query, pageRequest, resolved, projectionType);
        }

        @Override
        public <X extends P> ScrollResult<X> queryScroll(PAGE query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
            ProjectionSource resolved = firstSupporting(this::pageSourceSupported, projectionType);
            return AbstractProjectionQuery.this.queryScroll(query, cursor, pageSize, resolved, projectionType);
        }

        private boolean pageSourceSupported(ProjectionSource src) {
            try {
                registry.getPagedSearcher(src, pageType);
                return true;
            } catch (ProjectionSearcherNotFoundException e) {
                return false;
            }
        }

        /** 取链上第一个支持该条件族的源；均不支持抛 {@link ProjectionSearcherNotFoundException}。 */
        private ProjectionSource firstSupporting(java.util.function.Predicate<ProjectionSource> support, Class<?> projectionType) {
            return sources.stream()
                    .filter(support)
                    .findFirst()
                    .orElseThrow(() -> new ProjectionSearcherNotFoundException(
                            "回源链上无任何源支持条件族 " + pageType.getSimpleName()
                                    + "；可用源：[" + sources.stream().map(ProjectionSource::id).collect(java.util.stream.Collectors.joining(", ")) + "]"));
        }
    }

    /** 绑定指定源的查询视图：把源透传给私有查询方法。 */
    private final class SourceScopedQuery implements IProjectionSourceQuery<ID, P, ONE, LIST, PAGE> {

        private final ProjectionSource source;

        private SourceScopedQuery(ProjectionSource source) {
            this.source = source;
        }

        @Override
        public ProjectionSource source() {
            return source;
        }

        @Override
        public <X extends P> X queryById(ID id, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryById(id, source, projectionType);
        }

        @Override
        public <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryByIds(ids, source, projectionType);
        }

        @Override
        public <X extends P> X queryOne(ONE query, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryOne(query, source, projectionType);
        }

        @Override
        public <X extends P> List<X> queryList(LIST query, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryList(query, source, projectionType);
        }

        @Override
        public <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryPage(query, pageRequest, source, projectionType);
        }

        @Override
        public <X extends P> ScrollResult<X> queryScroll(PAGE query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryScroll(query, cursor, pageSize, source, projectionType);
        }
    }

    // ===================== 可观测钩子（开放问题 P8） =====================

    /**
     * 每次实际查询某源时回调，供接入方按需统计命中源 / 回源次数。
     * core 不内置实现，子类或接入层可覆盖。
     *
     * @param source 实际被选用的源
     * @param projectionType 目标投影类型
     */
    protected void onSourceResolved(ProjectionSource source, Class<?> projectionType) {
        // 默认空实现
    }

    // ===================== 类型擦除桥接 =====================

    @SuppressWarnings("unchecked")
    private <X> X cast(Object value) {
        return (X) value;
    }

    @SuppressWarnings("unchecked")
    private <X> List<X> castList(List<?> values) {
        return (List<X>) values;
    }
}
