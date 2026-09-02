package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.application.IQueryApplicationService;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单读服务：CQRS 读侧的应用层门面，按查询方式暴露能力，投影类型由调用方指定。
 *
 * <p>方法按**查询方式**组织，与领域查询契约 {@link IOrderQuery} 一一对应：按主键、批量主键、
 * 单条、列表、分页、滚动。每个方法接收 {@code Class<X> projectionType}，由调用方指定要返回
 * 哪种投影（如详情投影或概要投影）；本服务不做投影类型的选择，也不按视图拆分子方法——
 * 这样新增投影时本服务无需改动。</p>
 *
 * <p>调用方为用户接口层，属同一进程内的直接调用，故传递 {@code Class} 类型令牌是安全的；
 * 跨进程边界（如 Dubbo）不得透传该参数，由用户接口层转换为确定的 DTO 返回类型后再对外暴露。</p>
 *
 * <p>本服务只查询本聚合的读模型，不聚合其他聚合的数据；跨聚合组装由更上层的编排方完成。
 * 入参校验不在此处硬编码——待框架提供统一的入参校验能力后由框架统一承载。</p>
 *
 * @author wizard-lee
 */
@Service
public class OrderReadService implements IQueryApplicationService {

    private final IOrderQuery orderQuery;

    public OrderReadService(IOrderQuery orderQuery) {
        this.orderQuery = orderQuery;
    }

    /**
     * 按主键查询单个投影。
     *
     * @param orderId 订单号
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 命中的投影；未命中返回 null
     */
    public <X extends IOrderProjection> X queryById(Long orderId, Class<X> projectionType) {
        return orderQuery.queryById(orderId, projectionType);
    }

    /**
     * 按主键查询单个投影，并显式指定取数来源的物理投影类型。
     *
     * <p>与 {@link #queryById(Long, Class)} 的区别：后者按目标投影自动反查唯一来源，
     * 本方法由调用方直接指定来源，用于同一业务子投影可从多个物理副本（如 ES / Redis）取数的场景。</p>
     *
     * @param orderId 订单号
     * @param sourceProjection 取数来源的物理投影类型，如 {@code OrderEsProjection} / {@code OrderCacheProjection}
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 命中的投影；未命中返回 null
     */
    public <X extends IOrderProjection> X queryById(
            Long orderId,
            Class<?> sourceProjection,
            Class<X> projectionType) {
        return orderQuery.queryById(orderId, sourceProjection, projectionType);
    }

    /**
     * 按批量主键查询投影列表。
     *
     * @param orderIds 订单号列表
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 命中的投影列表；无结果返回空列表
     */
    public <X extends IOrderProjection> List<X> queryByIds(List<Long> orderIds, Class<X> projectionType) {
        return orderQuery.queryByIds(orderIds, projectionType);
    }

    /**
     * 按条件查询单个投影，匹配多条时取首条。
     *
     * @param query 单条查询条件
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 命中的投影；未命中返回 null
     */
    public <X extends IOrderProjection> X queryOne(OrderOneQuery query, Class<X> projectionType) {
        return orderQuery.queryOne(query, projectionType);
    }

    /**
     * 按条件查询投影列表。
     *
     * @param query 列表查询条件
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 命中的投影列表；无结果返回空列表
     */
    public <X extends IOrderProjection> List<X> queryList(OrderListQuery query, Class<X> projectionType) {
        return orderQuery.queryList(query, projectionType);
    }

    /**
     * 按条件分页查询投影，分页在检索器侧完成。
     *
     * @param query 分页查询条件
     * @param pageRequest 分页请求
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 分页结果，含当页数据与总记录数
     */
    public <X extends IOrderProjection> PageResult<X> queryPage(
            OrderPageQuery query,
            PageRequest pageRequest,
            Class<X> projectionType) {
        return orderQuery.queryPage(query, pageRequest, projectionType);
    }

    /**
     * 按条件滚动查询投影，用于深翻页与全量拉取。
     *
     * @param query 滚动查询条件
     * @param cursor 游标位置，首次查询传 {@link ScrollPosition#initial()}
     * @param pageSize 每批大小
     * @param projectionType 目标投影类型
     * @param <X> 投影子类型
     * @return 滚动结果；nextCursor 为 null 表示已到末页
     */
    public <X extends IOrderProjection> ScrollResult<X> queryScroll(
            OrderPageQuery query,
            ScrollPosition cursor,
            int pageSize,
            Class<X> projectionType) {
        return orderQuery.queryScroll(query, cursor, pageSize, projectionType);
    }
}
