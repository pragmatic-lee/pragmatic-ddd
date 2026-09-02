package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 投影源登记中心：管理读侧「源」与其挂接的投影器 / 检索器 / 裁剪器。
 *
 * <p>寻址第一维是 {@link ProjectionSource}（一份物理副本），源确定后其全量投影类型唯一确定，
 * 因此检索器与裁剪器不再以「投影类型」为键，而以「源」为键：
 * <ul>
 *     <li>检索器：按 (源, 条件族) 定位。</li>
 *     <li>裁剪器：按 (源, 子投影) 定位。</li>
 * </ul>
 *
 * <p>注册期强约束（违反即 {@link ProjectionSourceConflictException}）：
 * <ul>
 *     <li>源 id 全局唯一；</li>
 *     <li>同一 (源, 条件族) 不可重复绑定不同检索器；</li>
 *     <li>同一 (源, 子投影) 不可重复绑定不同裁剪器。</li>
 * </ul>
 *
 * <p>同一全量投影类可同时登记到多个源：多份异构副本共存是合法需求，
 * 未指定源且多源又无默认源时，首次查询抛 {@link ProjectionSourceAmbiguousException}。
 *
 * <p>多源共存是合法需求（同一业务子投影可从多个副本取数）；未指定源且多源又无默认源时，
 * 首次查询抛 {@link ProjectionSourceAmbiguousException}。</p>
 *
 * @author wizard-lee
 */
public class ProjectorRegistry {

    /** 源 id -> 源实例。 */
    private final Map<ProjectionSource, AbstractProjectionSource<?, ?>> sources = new ConcurrentHashMap<>();

    /** 全量投影类 -> 承载该全量投影的所有源（多源共存，一份投影可落多份异构副本）。 */
    private final Map<Class<?>, Set<ProjectionSource>> sourcesByProjection = new ConcurrentHashMap<>();

    /** 子投影类 -> 提供该子投影裁剪的所有源（多源共存）。 */
    private final Map<Class<?>, Set<ProjectionSource>> sourcesBySubProjection = new ConcurrentHashMap<>();

    /** 子投影类 -> 默认源（多源时未指定源则取默认源）。 */
    private final Map<Class<?>, ProjectionSource> defaultSources = new ConcurrentHashMap<>();

    /**
     * 登记一个源及其全部挂接构件。注册期仅校验源 id 全局唯一；
     * 同一全量投影类可同时登记到多个源（多份异构副本共存）。
     *
     * @param source 源实例
     * @param <T> 聚合根类型
     * @param <P> 全量投影类型
     */
    public <T extends AggregateRoot<?>, P extends IAggregateProjection> void register(AbstractProjectionSource<T, P> source) {
        AbstractProjectionSource<?, ?> previous = sources.putIfAbsent(source.source(), source);
        if (previous != null && previous != source) {
            throw new ProjectionSourceConflictException(
                    "源 id 重复：" + source.source().id() + " 已登记于 " + previous.getClass().getSimpleName());
        }
        sourcesByProjection
                .computeIfAbsent(source.projectionType(), k -> new HashSet<>())
                .add(source.source());
        source.reducers().keySet().forEach(sub -> sourcesBySubProjection
                .computeIfAbsent(sub, k -> new HashSet<>())
                .add(source.source()));
    }

    /**
     * 登记某子投影的默认源。多源时未指定源则取默认源；未指定且无默认则查询抛歧义。
     *
     * @param subProjection 子投影类型
     * @param source 默认源
     */
    public void registerDefaultSource(Class<?> subProjection, ProjectionSource source) {
        defaultSources.put(subProjection, source);
    }

    /** 取源实例；未登记抛 {@link ProjectionSourceNotFoundException}。供查询链路（源必须存在）。 */
    public AbstractProjectionSource<?, ?> getSource(ProjectionSource source) {
        return Optional.ofNullable(sources.get(source))
                .orElseThrow(() -> new ProjectionSourceNotFoundException("源未登记：" + source.id()));
    }

    /** 取源实例；未登记返回 empty。供写侧门面（装配可渐进，缺源静默跳过）。 */
    public Optional<AbstractProjectionSource<?, ?>> findSource(ProjectionSource source) {
        return Optional.ofNullable(sources.get(source));
    }

    /** 取源投影器；未登记抛 {@link ProjectionSourceNotFoundException}。 */
    @SuppressWarnings("unchecked")
    public <T extends AggregateRoot<?>, P extends IAggregateProjection> IAggregateProjector<T, P> getProjector(ProjectionSource source) {
        return (IAggregateProjector<T, P>) getSource(source).projector();
    }

    /** 取按 id 检索器；未登记或源无该检索器抛 {@link ProjectionSourceNotFoundException}。 */
    @SuppressWarnings("unchecked")
    public <P extends IAggregateProjection> IProjectionByIdSearcher<P> getByIdSearcher(ProjectionSource source) {
        AbstractProjectionSource<?, ?> src = getSource(source);
        return (IProjectionByIdSearcher<P>) src.idSearcher()
                .orElseThrow(() -> new ProjectionSourceNotFoundException(
                        "源 " + source.id() + " 未绑定按主键检索器"));
    }

    /** 取按条件检索器；未登记或源无该条件族检索器抛 {@link ProjectionSearcherNotFoundException}。 */
    @SuppressWarnings("unchecked")
    public <C extends QueryCriteria, P extends IAggregateProjection> IProjectionSearcher<C, P> getSearcher(
            ProjectionSource source, Class<C> criteriaType) {
        AbstractProjectionSource<?, ?> src = getSource(source);
        IProjectionSearcher<?, ?> searcher = src.searchers().get(criteriaType);
        if (searcher == null) {
            throw new ProjectionSearcherNotFoundException(
                    "源 " + source.id() + " 未登记条件族 " + criteriaType.getSimpleName()
                            + " 的检索器；已支持：" + supportedCriteria(src.searchers().keySet()));
        }
        return (IProjectionSearcher<C, P>) searcher;
    }

    /** 取分页检索器；未登记或源无该条件族检索器抛 {@link ProjectionSearcherNotFoundException}。 */
    @SuppressWarnings("unchecked")
    public <C extends PageQueryCriteria, P extends IAggregateProjection> IProjectionPagedSearcher<C, P> getPagedSearcher(
            ProjectionSource source, Class<C> criteriaType) {
        AbstractProjectionSource<?, ?> src = getSource(source);
        IProjectionPagedSearcher<?, ?> searcher = src.pagedSearchers().get(criteriaType);
        if (searcher == null) {
            throw new ProjectionSearcherNotFoundException(
                    "源 " + source.id() + " 未登记分页条件族 " + criteriaType.getSimpleName()
                            + " 的检索器；已支持：" + supportedCriteria(src.pagedSearchers().keySet()));
        }
        return (IProjectionPagedSearcher<C, P>) searcher;
    }

    /**
     * 取裁剪器；未登记或源下无该子投影裁剪器抛 {@link ProjectionSourceNotFoundException}。
     */
    @SuppressWarnings("unchecked")
    public <SRC extends IAggregateProjection, SUB extends IAggregateProjection> IProjectionReducer<SRC, SUB> getReducer(
            ProjectionSource source, Class<SUB> subProjection) {
        AbstractProjectionSource<?, ?> src = getSource(source);
        IProjectionReducer<?, ?> reducer = src.reducers().get(subProjection);
        if (reducer == null) {
            throw new ProjectionSourceNotFoundException(
                    "源 " + source.id() + " 未登记子投影 " + subProjection.getSimpleName() + " 的裁剪器");
        }
        return (IProjectionReducer<SRC, SUB>) reducer;
    }

    /** 由全量投影类取其一承载源（多源时优先取已登记的首个）；无源返回空。 */
    public Optional<ProjectionSource> fullProjectionOf(Class<?> projectionType) {
        Set<ProjectionSource> sources = sourcesByProjection.get(projectionType);
        return sources == null || sources.isEmpty()
                ? Optional.empty()
                : Optional.of(sources.iterator().next());
    }

    /** 由子投影类取提供该子投影的全部源（多源共存）。 */
    public Set<ProjectionSource> sourcesOf(Class<?> subProjection) {
        return sourcesBySubProjection.getOrDefault(subProjection, Set.of());
    }

    /**
     * 解析查询所使用的源：指定源则校验通过后返回；未指定源则按子投影/全量投影定位，
     * 多源取默认源，无默认则抛 {@link ProjectionSourceAmbiguousException}。
     *
     * @param projectionType 目标投影类型（全量或子投影）
     * @param source 调用方显式指定的源，可为 null
     * @param <P> 投影类型
     * @return 解析后的源
     */
    @SuppressWarnings("unchecked")
    public <P extends IAggregateProjection> ProjectionSource resolveSource(Class<P> projectionType, ProjectionSource source) {
        if (source != null) {
            return resolveSpecifiedSource(projectionType, source);
        }
        Optional<ProjectionSource> byFull = fullProjectionOf(projectionType);
        if (byFull.isPresent()) {
            return byFull.get();
        }
        Set<ProjectionSource> candidates = sourcesOf(projectionType);
        if (candidates.isEmpty()) {
            throw new ProjectionSourceNotFoundException("无任何源提供投影 " + projectionType.getSimpleName());
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        ProjectionSource defaultSource = defaultSources.get(projectionType);
        if (defaultSource != null) {
            return defaultSource;
        }
        throw ProjectionSourceAmbiguousException.of(projectionType, candidates);
    }

    private <P extends IAggregateProjection> ProjectionSource resolveSpecifiedSource(Class<P> projectionType, ProjectionSource source) {
        AbstractProjectionSource<?, ?> src = Optional.ofNullable(sources.get(source))
                .orElseThrow(() -> new ProjectionSourceNotFoundException("源未登记：" + source.id()));
        boolean isFull = src.projectionType().equals(projectionType);
        boolean isSub = src.reducers().containsKey(projectionType);
        if (!isFull && !isSub) {
            throw new ProjectionSourceNotFoundException(
                    "源 " + source.id() + " 既不承载全量投影 " + projectionType.getSimpleName()
                            + " 也未登记其子投影裁剪器");
        }
        return source;
    }

    private static String supportedCriteria(Set<Class<?>> criteriaTypes) {
        return criteriaTypes.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
    }

    /** 兼容旧返回的占位（无实际用途，保留避免破坏既有 toString）。 */
    public Map<ProjectionSource, AbstractProjectionSource<?, ?>> sources() {
        return sources;
    }

    /** 给定 storeId 解析源标识。供 {@link AggregateProjectorSupport} 由对账目标桥接（sync 路径）。 */
    public Optional<ProjectionSource> sourceById(String id) {
        Objects.requireNonNull(id, "id");
        return sources.keySet().stream()
                .filter(s -> s.id().equals(id))
                .findFirst();
    }

    /** 给定 storeId 取源实例（源 id 与写侧 ReconciliationTarget.storeId 同名）。供 purge 路径。 */
    public Optional<AbstractProjectionSource<?, ?>> getSource(String id) {
        Objects.requireNonNull(id, "id");
        return sources.values().stream()
                .filter(s -> s.source().id().equals(id))
                .findFirst();
    }

    /** 取源对应的对账目标（id 同名，由源派生）。 */
    public Optional<ReconciliationTarget> targetOf(ProjectionSource source) {
        return Optional.ofNullable(sources.get(source)).map(AbstractProjectionSource::target);
    }
}
