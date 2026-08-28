package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 按多个聚合 ID 批量查询投影。
 *
 * @param <ID> 聚合 ID 类型
 * @param <P>  投影类型，必须是 {@link IAggregateProjection} 的子类型
 *
 * @author wizard-lee
 */
public interface IQueryByIds<ID, P extends IAggregateProjection> {

    /**
     * @param projectionType 目标投影类型，决定返回的投影具体子类型
     * @param <X>            实际返回的投影子类型，必须是 {@code P} 的子类型
     * @return 命中的投影列表；建议保持与入参 ID 顺序一致；未命中任何记录时返回空列表而非 null。
     */
    <X extends P> List<X> queryByIds(List<ID> ids, Class<X> projectionType);
}
