package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 按主键 / 批量主键直取投影的检索器，与按条件检索的 {@link IProjectionSearcher} 平级。
 * 对应 {@code IQueryById} / {@code IQueryByIds}，不依赖任何条件族（无 QueryCriteria）。
 * 由各集成模块（ES / Redis / 读表连接器）实现，core 只定义中立接口。
 *
 * <p>注册键仅 {@code (projectionType)} 一维，由 {@link ProjectorRegistry#getByIdSearcher(Class)} 按型定位。</p>
 *
 * @param <P> 投影类型
 * @author wizard-lee
 */
public interface IProjectionByIdSearcher<P extends IAggregateProjection> {

    /** 本检索器服务的投影类型，供按型定位。 */
    Class<P> projectionType();

    /** 按主键直取单条投影；未命中返回 null。 */
    P getById(Object id, Class<P> projectionType);

    /** 按批量主键取回投影列表（无结果返回空列表，不返回 null）。 */
    List<P> getByIds(List<Object> ids, Class<P> projectionType);
}
