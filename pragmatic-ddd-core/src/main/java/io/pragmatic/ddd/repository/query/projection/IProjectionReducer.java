package io.pragmatic.ddd.repository.query.projection;

/**
 * 投影裁剪器：将索引级全量投影裁剪（或派生）为业务子投影。
 *
 * <p>与 {@link IAggregateProjector} 平级且职责不同：后者是「写模型聚合根 → 投影」，
 * 本接口是「索引级全量投影 → 业务子投影」。二者不可互相替代——
 * {@code IAggregateProjector} 的源类型上界为 {@code AggregateRoot}，而索引级全量投影是
 * 存储文档形状的数据容器，并非聚合根。</p>
 *
 * <p>读侧取数分两跳：检索器按 (条件类型, 索引级全量投影类型) 从存储取回全量投影，
 * 再由本接口在 Java 内存中裁剪为调用方指定的子投影。检索器因此不再与业务投影耦合，
 * 检索器数量由「条件族数 × 投影数」降为「条件族数 × 索引数」。</p>
 *
 * <p>裁剪能力覆盖字段裁剪、层级重排与派生计算——这些是存储侧 {@code _source} 过滤
 * 无法表达的（后者只能裁剪字段路径，不能改变字段层级）。</p>
 *
 * <p>一个实例服务一个 (源投影, 子投影) 组合，由 {@link ProjectorRegistry}
 * 按 {@code (sourceType, projectionType)} 二维键定位。</p>
 *
 * @param <S> 源投影类型（索引级全量投影，对齐某物理索引的文档形状）
 * @param <P> 目标投影类型（业务子投影）
 *
 * @author wizard-lee
 */
public interface IProjectionReducer<S extends IAggregateProjection, P extends IAggregateProjection> {

    /** 源投影类型（索引级全量投影），供按型定位。 */
    Class<S> sourceType();

    /** 产出的子投影类型，供按型定位。 */
    Class<P> projectionType();

    /**
     * 将索引级全量投影裁剪为业务子投影。
     *
     * <p>实现必须是纯函数：无状态、无存储访问、无远程调用，可独立单测。
     * 裁剪不改变集合规模——分页 / 滚动在检索器侧完成，本方法只做逐条转换。</p>
     *
     * @param source 索引级全量投影；为 null 时返回 null，由调用方过滤
     * @return 业务子投影
     */
    P reduce(S source);
}
