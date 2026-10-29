package io.pragmatic.ddd.repository.query.projection;

import java.util.List;

/**
 * 按主键 / 批量主键直取投影的检索器，与按条件检索的 {@link IOneQuerySearcher} 等平级。
 * 不依赖任何条件族（无 QueryCriteria），由各集成模块（ES / Redis / 读表连接器）实现，core 只定义中立接口。
 *
 * <p>其服务的是<b>该读源承载的投影类型</b>：副本读源为索引级全量投影（对齐物理存储的
 * 文档形状），同源直读为行投影（对齐本次 SQL 的 SELECT 列）；均为具体投影类，
 * 而非投影体系接口。子投影由 {@link IReducer} 在 Java 内存中二次裁剪。</p>
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
