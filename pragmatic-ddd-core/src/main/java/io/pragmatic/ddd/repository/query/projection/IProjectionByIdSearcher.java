package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.criteria.QueryCriteria;

import java.util.List;

/**
 * 按主键 / 批量主键直取投影的检索器，与按条件检索的 {@link IProjectionSearcher} 平级。
 * 对应 {@code IQueryById} / {@code IQueryByIds}，不依赖任何条件族（无 QueryCriteria）。
 * 由各集成模块（ES / Redis / 读表连接器）实现，core 只定义中立接口。
 *
 * <p>注册键仅 {@code (projectionType)} 一维，由 {@link ProjectorRegistry#getByIdSearcher(ProjectionSource)} 按型定位。
 * 其服务的投影类型是<b>索引级全量投影类型</b>——对齐某物理存储索引文档形状的
 * 具体投影类，而非投影体系接口；子投影由 {@link IProjectionReducer} 在 Java 内存中二次裁剪。</p>
 *
 * @param <P> 投影类型
 * @author wizard-lee
 */
public interface IProjectionByIdSearcher<P extends IAggregateProjection> {

    /** 按主键直取单条投影；未命中返回 null。 */
    P getById(Object id);

    /** 按批量主键取回投影列表（无结果返回空列表，不返回 null）。 */
    List<P> getByIds(List<Object> ids);
}
