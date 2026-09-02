package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;

import java.util.List;

/**
 * 分页 / 滚动投影检索器：从某异构存储按业务条件做分页或滚动取回投影。
 * 与 {@link IProjectionSearcher}（列表 / 单条）平级，专注带分页语义的检索。
 * 条件上界限定为 {@link PageQueryCriteria} 子类（分页与滚动同族同条件）。
 * 由各集成模块（ES / Redis / 读表连接器）实现，core 只定义中立接口。
 *
 * <p>一个 searcher 实例服务一个聚合族的某类条件 + 某索引级全量投影类型，由
 * {@link ProjectorRegistry} 按 {@code (criteriaType, projectionType)} 二维键定位。
 * 其服务的投影类型是对齐某物理存储索引文档形状的具体投影类，
 * 而非业务子投影；子投影由 {@link IProjectionReducer} 在 Java 内存中二次裁剪。</p>
 *
 * <p>分页 / 滚动在本接口内完成，裁剪只做逐条转换、不改变集合规模；
 * 因此 {@link PageResult#totalCount()} 必须取自裁剪前的结果。</p>
 *
 * @param <C> 业务条件类型（继承 {@link PageQueryCriteria}，如 OrderPageQuery）
 * @param <P> 投影类型
 * @author wizard-lee
 */
public interface IProjectionPagedSearcher<C extends PageQueryCriteria, P extends IAggregateProjection> {

    /** 本检索器服务的业务条件类型，供按型定位。 */
    Class<C> criteriaType();

    /**
     * 分页检索：返回带总量与请求信息的结果页。
     * 检索器只服务所属源，投影类型由源持有，故此处无需再传投影类型。
     */
    PageResult<P> searchPage(C condition, PageRequest pageRequest);

    /**
     * 滚动检索：返回本页数据与下一页游标（游标为 null 表示已到末页）。
     * 检索器只服务所属源，投影类型由源持有，故此处无需再传投影类型。
     */
    ScrollResult<P> searchScroll(C condition, ScrollPosition cursor, int pageSize);
}
