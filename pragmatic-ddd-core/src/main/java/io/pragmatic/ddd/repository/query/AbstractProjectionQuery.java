package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.ListQueryCriteria;
import io.pragmatic.ddd.repository.query.criteria.OneQueryCriteria;
import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IProjectionReducer;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 聚合查询抽象基类：把「按投影类型选路 / 检索 / 裁剪 / 缺省短路」的通用流程上收，
 * 各聚合的查询实现只需继承本类并注入 {@link ProjectorRegistry} 与三族条件类型即可。
 *
 * <p>查询链路统一为三跳：
 * <ol>
 *     <li>{@code candidates} 解析候选源（未指定源按投影类型 + 默认源定位；指定源 / 回源链按能力过滤）；</li>
 *     <li>目标为全量投影时短路直返（分页 / 滚动复用结果页实例）；</li>
 *     <li>否则按 (源, 子投影) 取裁剪器逐条 {@code reduce}。</li>
 * </ol>
 *
 * <p>支持两种视图：默认视图（隐式选路）与 {@link #source(ProjectionSource)} 指定源视图，
 * 以及 {@link #fallbackChain(List)} 多源回源视图。三者方法调用形状一致。</p>
 *
 * <p>选源可内置：子类覆写 {@link #fallbackChain()} 声明读侧默认回源链后，
 * 6 个查询方法按该链顺序寻址，调用方无需感知源；链上源不支持本次查询时自动跳过。</p>
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

    /**
     * 读侧默认回源链：子类覆写即把选源内置到读服务，调用方只传目标投影类型。
     *
     * <p>返回非空列表时，本类 6 个查询方法按该链顺序寻址，链上源不支持本次查询时自动跳过；
     * 返回空列表（默认）表示不启用，沿用「按投影类型 + 默认源」选路。</p>
     *
     * @return 回源顺序，越靠前优先级越高；空列表表示未启用
     */
    protected List<ProjectionSource> fallbackChain() {
        return List.of();
    }

    /** 取内置回源链；未启用返回 null（表示未指定源）。 */
    private List<ProjectionSource> defaultChain() {
        List<ProjectionSource> chain = fallbackChain();
        return chain == null || chain.isEmpty() ? null : List.copyOf(chain);
    }

    /** 按主键查询：默认视图。 */
    @Override
    public <X extends P> X queryById(ID id, Class<X> projectionType) {
        return queryById(id, defaultChain(), projectionType);
    }

    /** 按批量主键查询：默认视图。 */
    @Override
    public <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType) {
        return queryByIds(ids, defaultChain(), projectionType);
    }

    /** 按单条件查询：默认视图。 */
    @Override
    public <X extends P> X queryOne(ONE query, Class<X> projectionType) {
        return queryOne(query, defaultChain(), projectionType);
    }

    /** 按列表条件查询：默认视图。 */
    @Override
    public <X extends P> List<X> queryList(LIST query, Class<X> projectionType) {
        return queryList(query, defaultChain(), projectionType);
    }

    /** 分页查询：默认视图。 */
    @Override
    public <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, Class<X> projectionType) {
        return queryPage(query, pageRequest, defaultChain(), projectionType);
    }

    /** 滚动查询：默认视图。 */
    @Override
    public <X extends P> ScrollResult<X> queryScroll(
            PAGE query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
        return queryScroll(query, cursor, pageSize, defaultChain(), projectionType);
    }

    // ===================== 候选源解析 =====================

    /**
     * 解析本次查询的候选源：未指定源时按投影类型 + 默认源定位单源；
     * 指定源或回源链时按「源支持该投影类型」+「源具备本次查询所需检索器」过滤，
     * 过滤后为空抛 {@link ProjectionSourceNotFoundException}。
     *
     * @param sources 指定源 / 回源链，null 表示未指定源
     * @param projectionType 目标投影类型
     * @param capability 本次查询所需的检索器能力
     * @param <X> 目标投影类型
     * @return 候选源列表，按链序排列，非空
     */
    private <X extends IAggregateProjection> List<ProjectionSource> candidates(
            List<ProjectionSource> sources,
            Class<X> projectionType,
            Predicate<ProjectionSource> capability) {
        if (sources == null) {
            return List.of(registry.resolveSource(projectionType, null));
        }
        List<ProjectionSource> hit = sources.stream()
                .filter(src -> registry.supportsProjection(src, projectionType))
                .filter(capability)
                .toList();
        if (hit.isEmpty()) {
            throw new ProjectionSourceNotFoundException(
                    "无可用源提供投影 " + projectionType.getSimpleName() + "；候选源：" + ids(sources));
        }
        return hit;
    }

    /** 取首个候选源：分页 / 滚动不回源，只取链上首个支持者。 */
    private <X extends IAggregateProjection> ProjectionSource firstCandidate(
            List<ProjectionSource> sources,
            Class<X> projectionType,
            Predicate<ProjectionSource> capability) {
        return candidates(sources, projectionType, capability).get(0);
    }

    private static String ids(List<ProjectionSource> sources) {
        return sources.stream()
                .map(ProjectionSource::id)
                .collect(Collectors.joining(", "));
    }

    // ===================== 查询实现（候选源 + 回源） =====================

    @SuppressWarnings("unchecked")
    private <X extends P> X queryById(ID id, List<ProjectionSource> sources, Class<X> projectionType) {
        for (ProjectionSource src : candidates(sources, projectionType, registry::hasByIdSearcher)) {
            onSourceResolved(src, projectionType);
            if (isFullProjection(src, projectionType)) {
                X hit = cast(registry.getByIdSearcher(src).getById(id));
                if (hit != null) {
                    return hit;
                }
                continue;
            }
            IProjectionReducer<?, X> reducer = registry.getReducer(src, projectionType);
            IAggregateProjection full = registry.getByIdSearcher(src).getById(id);
            if (full != null) {
                return reduceWith(reducer, full);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <X extends P> List<X> queryByIds(List<ID> ids, List<ProjectionSource> sources, Class<X> projectionType) {
        List<Object> objectIds = (List<Object>) ids;
        for (ProjectionSource src : candidates(sources, projectionType, registry::hasByIdSearcher)) {
            onSourceResolved(src, projectionType);
            if (isFullProjection(src, projectionType)) {
                List<X> hit = castList(registry.getByIdSearcher(src).getByIds(objectIds));
                if (hit != null && !hit.isEmpty()) {
                    return hit;
                }
                continue;
            }
            IProjectionReducer<?, X> reducer = registry.getReducer(src, projectionType);
            List<X> reduced = reduceAll(reducer, registry.getByIdSearcher(src).getByIds(objectIds));
            if (!reduced.isEmpty()) {
                return reduced;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private <X extends P> X queryOne(ONE query, List<ProjectionSource> sources, Class<X> projectionType) {
        for (ProjectionSource src : candidates(sources, projectionType, s -> registry.hasSearcher(s, oneType))) {
            onSourceResolved(src, projectionType);
            if (isFullProjection(src, projectionType)) {
                X hit = cast(registry.getSearcher(src, oneType).search(query).stream().findFirst().orElse(null));
                if (hit != null) {
                    return hit;
                }
                continue;
            }
            IProjectionReducer<?, X> reducer = registry.getReducer(src, projectionType);
            X reduced = registry.getSearcher(src, oneType).search(query).stream()
                    .findFirst()
                    .map(full -> reduceWith(reducer, full))
                    .orElse(null);
            if (reduced != null) {
                return reduced;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <X extends P> List<X> queryList(LIST query, List<ProjectionSource> sources, Class<X> projectionType) {
        for (ProjectionSource src : candidates(sources, projectionType, s -> registry.hasSearcher(s, listType))) {
            onSourceResolved(src, projectionType);
            if (isFullProjection(src, projectionType)) {
                List<X> hit = castList(registry.getSearcher(src, listType).search(query));
                if (hit != null && !hit.isEmpty()) {
                    return hit;
                }
                continue;
            }
            IProjectionReducer<?, X> reducer = registry.getReducer(src, projectionType);
            List<X> reduced = reduceAll(reducer, registry.getSearcher(src, listType).search(query));
            if (!reduced.isEmpty()) {
                return reduced;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private <X extends P> PageResult<X> queryPage(
            PAGE query, PageRequest pageRequest, List<ProjectionSource> sources, Class<X> projectionType) {
        ProjectionSource resolved = firstCandidate(
                sources, projectionType, src -> registry.hasPagedSearcher(src, pageType));
        onSourceResolved(resolved, projectionType);
        PageResult<? extends IAggregateProjection> page =
                registry.getPagedSearcher(resolved, pageType).searchPage(query, pageRequest);
        if (isFullProjection(resolved, projectionType)) {
            return PageResult.of(castList(page.data()), page.totalCount(), page.request());
        }
        IProjectionReducer<?, X> reducer = registry.getReducer(resolved, projectionType);
        return PageResult.of(reduceAll(reducer, page.data()), page.totalCount(), page.request());
    }

    @SuppressWarnings("unchecked")
    private <X extends P> ScrollResult<X> queryScroll(
            PAGE query,
            ScrollPosition cursor,
            int pageSize,
            List<ProjectionSource> sources,
            Class<X> projectionType) {
        ProjectionSource resolved = firstCandidate(
                sources, projectionType, src -> registry.hasPagedSearcher(src, pageType));
        onSourceResolved(resolved, projectionType);
        ScrollResult<? extends IAggregateProjection> scroll =
                registry.getPagedSearcher(resolved, pageType).searchScroll(query, cursor, pageSize);
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

    // ===================== 视图：回源链 / 指定源 =====================

    /** 按回源顺序查询：前源未取到结果时自动推进下一源；分页 / 滚动取链上首个支持者。 */
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
            return AbstractProjectionQuery.this.queryById(id, sources, projectionType);
        }

        @Override
        public <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryByIds(ids, sources, projectionType);
        }

        @Override
        public <X extends P> X queryOne(ONE query, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryOne(query, sources, projectionType);
        }

        @Override
        public <X extends P> List<X> queryList(LIST query, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryList(query, sources, projectionType);
        }

        @Override
        public <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryPage(query, pageRequest, sources, projectionType);
        }

        @Override
        public <X extends P> ScrollResult<X> queryScroll(
                PAGE query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryScroll(query, cursor, pageSize, sources, projectionType);
        }
    }

    /** 绑定指定源的查询视图：等价于长度为一的回源链。 */
    private final class SourceScopedQuery implements IProjectionSourceQuery<ID, P, ONE, LIST, PAGE> {

        private final List<ProjectionSource> sources;

        private SourceScopedQuery(ProjectionSource source) {
            this.sources = List.of(source);
        }

        @Override
        public ProjectionSource source() {
            return sources.get(0);
        }

        @Override
        public <X extends P> X queryById(ID id, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryById(id, sources, projectionType);
        }

        @Override
        public <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryByIds(ids, sources, projectionType);
        }

        @Override
        public <X extends P> X queryOne(ONE query, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryOne(query, sources, projectionType);
        }

        @Override
        public <X extends P> List<X> queryList(LIST query, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryList(query, sources, projectionType);
        }

        @Override
        public <X extends P> PageResult<X> queryPage(PAGE query, PageRequest pageRequest, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryPage(query, pageRequest, sources, projectionType);
        }

        @Override
        public <X extends P> ScrollResult<X> queryScroll(
                PAGE query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
            return AbstractProjectionQuery.this.queryScroll(query, cursor, pageSize, sources, projectionType);
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
