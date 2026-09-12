package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceConflictException;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 投影源登记中心：管理读侧「源」的登记与按标识取用。
 *
 * <p>极薄化后只做 {@code sourceId → Source} 登记：写侧（物化 / 对账）与外部需要按 id 取源时使用。
 * 检索器与裁剪器均不再以其为定位入口——检索器由 Source 按需 implements 领域接口，
 * 裁剪器随 Source 走（见 {@link AbstractProjectionSource#getReducer(Class)}）。</p>
 *
 * <p>注册期强约束（违反即 {@link ProjectionSourceConflictException}）：源 id 全局唯一。</p>
 *
 * @author wizard-lee
 */
public class ProjectorRegistry {

    /** 源 id -> 源实例。 */
    private final Map<ProjectionSource, AbstractProjectionSource<?, ?, ?>> sources = new ConcurrentHashMap<>();

    /**
     * 登记一个源；同一源 id 重复登记不同实例视为冲突。
     *
     * @param source 源实例
     * @param <T> 聚合根类型
     * @param <ID> 聚合标识类型
     * @param <P> 全量投影类型
     */
    public <T extends AggregateRoot<ID>, ID, P extends IAggregateProjection> void register(
            AbstractProjectionSource<T, ID, P> source) {
        AbstractProjectionSource<?, ?, ?> previous = sources.putIfAbsent(source.getSource(), source);
        if (previous != null && previous != source) {
            throw new ProjectionSourceConflictException(
                    "源 id 重复：" + source.getSource().id() + " 已登记于 " + previous.getClass().getSimpleName());
        }
    }

    /**
     * 取源实例；未登记抛 {@link ProjectionSourceNotFoundException}。
     *
     * @param source 源标识
     * @return 源实例
     */
    public AbstractProjectionSource<?, ?, ?> getSource(ProjectionSource source) {
        return Optional.ofNullable(sources.get(source))
                .orElseThrow(() -> new ProjectionSourceNotFoundException("源未登记：" + source.id()));
    }

    /**
     * 取源实例；未登记返回 empty。
     *
     * @param source 源标识
     * @return 源实例
     */
    public Optional<AbstractProjectionSource<?, ?, ?>> findSource(ProjectionSource source) {
        return Optional.ofNullable(sources.get(source));
    }

    /**
     * 取源投影器；未登记抛 {@link ProjectionSourceNotFoundException}。
     *
     * @param source 源标识
     * @param <T> 聚合根类型
     * @param <ID> 聚合标识类型
     * @param <P> 全量投影类型
     * @return 投影器
     */
    @SuppressWarnings("unchecked")
    public <T extends AggregateRoot<ID>, ID, P extends IAggregateProjection> IAggregateProjector<T, P> getProjector(
            ProjectionSource source) {
        return (IAggregateProjector<T, P>) getSource(source).getProjector();
    }
}
