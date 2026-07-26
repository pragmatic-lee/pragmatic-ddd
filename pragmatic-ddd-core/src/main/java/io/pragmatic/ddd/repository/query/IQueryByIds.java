package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 按多个聚合 ID 批量查询投影。
 *
 * @param <ID>         聚合 ID 类型
 * @param <PROJECTION> 投影类型
 */
public interface IQueryByIds<ID, PROJECTION> {

    /**
     * @return 命中的投影列表；建议保持与入参 ID 顺序一致；未命中任何记录时返回空列表而非 null。
     */
    List<PROJECTION> queryByIds(List<ID> ids);
}
