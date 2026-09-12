package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;

/**
 * 分页 / 滚动查询族：从某异构存储按业务条件做分页或滚动取回投影。
 *
 * <p>条件泛型 {@code C} 的上界为框架抽象 {@link PageQueryCriteria}，分页与滚动同族同条件；
 * 实现方在 {@code implements} 时钉死为业务查询类型（如 {@code OrderPageQuery}）。</p>
 *
 * <p>分页 / 滚动在本接口内完成，裁剪只做逐条转换、不改变集合规模，
 * 因此 {@link PageResult#totalCount()} 必须取自裁剪前的结果。</p>
 *
 * @param <P> 投影类型
 * @param <C> 业务条件类型
 * @author wizard-lee
 */
public interface IPagedQuerySearcher<P extends IAggregateProjection, C extends PageQueryCriteria> {

    /**
     * 分页检索：返回带总量与请求信息的结果页。
     *
     * @param criteria 业务条件
     * @param pageRequest 分页请求
     * @return 结果页
     */
    PageResult<P> searchPage(C criteria, PageRequest pageRequest);

    /**
     * 滚动检索：返回本页数据与下一页游标（游标为 null 表示已到末页）。
     *
     * @param criteria 业务条件
     * @param cursor 滚动游标
     * @param pageSize 每页大小
     * @return 滚动结果
     */
    ScrollResult<P> searchScroll(C criteria, ScrollPosition cursor, int pageSize);
}
