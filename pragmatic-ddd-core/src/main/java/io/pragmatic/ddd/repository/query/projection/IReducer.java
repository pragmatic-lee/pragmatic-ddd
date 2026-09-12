package io.pragmatic.ddd.repository.query.projection;

/**
 * 投影裁剪器：将索引级全量投影裁剪（或派生）为业务子投影。
 *
 * <p>与 {@link IAggregateProjector} 平级且职责不同：后者是「写模型聚合根 → 投影」，
 * 本接口是「索引级全量投影 → 业务子投影」。二者不可互相替代——
 * {@code IAggregateProjector} 的源类型上界为 {@code AggregateRoot}，而索引级全量投影是
 * 存储文档形状的数据容器，并非聚合根。</p>
 *
 * <p>与 Source 强相关：其 {@code S} 即某个 Source 承载的全量投影类型，故一个 reducer
 * 只能由"产出该全量投影的 Source"持有。reducer 由 Source 构造时注入、经
 * {@link AbstractProjectionSource#getReducer(Class)} 按目标子投影类型定位；
 * 一个 Source 可注册多个 reducer（不同 {@code X}）。</p>
 *
 * <p>产出 {@code X} 由实现类签名 {@code implements IReducer<源投影, 子投影>} 编译期钉死，
 * 写 reducer 时即明确能裁出什么、写错类型编译失败。</p>
 *
 * <p>实现必须是纯函数：无状态、无存储访问、无远程调用，可独立单测。
 * 裁剪不改变集合规模——分页 / 滚动在检索器侧完成，本方法只做逐条转换。</p>
 *
 * @param <S> 源投影类型（索引级全量投影）
 * @param <X> 目标投影类型（业务子投影）
 * @author wizard-lee
 */
public interface IReducer<S extends IAggregateProjection, X extends IAggregateProjection> {

    /** 产出的子投影类型，供 Source 内部按型定位。 */
    Class<X> projectionType();

    /**
     * 将索引级全量投影裁剪为业务子投影。
     *
     * @param source 索引级全量投影；为 null 时返回 null，由调用方过滤
     * @return 业务子投影
     */
    X reduce(S source);
}
