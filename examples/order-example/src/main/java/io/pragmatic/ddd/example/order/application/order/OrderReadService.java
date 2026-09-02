package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;

import io.pragmatic.ddd.application.IQueryApplicationService;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.*;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单读侧应用服务。
 *
 * <p>直接复用框架的 {@link AbstractProjectionQuery} 完成「按投影类型定位检索器 / 裁剪器」的纯转发，
 * 并集中负责多源编排：{@code getById} / {@code getByIds} 以 Redis 为首选源、ES 为回退源，
 * 未命中 Redis 时由框架 {@code fallbackChain} 自动回退到 ES，不再单独维护领域查询接口与基础设施实现类。</p>
 *
 * @author wizard-lee
 */
@Service
public class OrderReadService
        extends AbstractProjectionQuery<Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery>
        implements IQueryApplicationService {

    /** Redis 源标识：与 {@link OrderCacheTargets#TARGET_REDIS_ORDERS} 的 storeId 保持一致。 */
    private static final ProjectionSource REDIS_SOURCE =
            ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId());

    /** ES 源标识：与 {@link OrderEsTargets#TARGET_ES_ORDERS} 的 storeId 保持一致。 */
    private static final ProjectionSource ES_SOURCE =
            ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId());

    public OrderReadService(ProjectorRegistry projectorRegistry) {
        super(projectorRegistry, OrderOneQuery.class, OrderListQuery.class, OrderPageQuery.class);
    }

    @Override
    public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
        return super.queryById(id, projectionType);
    }

    @Override
    public <X extends IOrderProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
        return super.queryByIds(ids, projectionType);
    }

    @Override
    public <X extends IOrderProjection> X queryOne(OrderOneQuery criteria, Class<X> projectionType) {
        return super.queryOne(criteria, projectionType);
    }

    @Override
    public <X extends IOrderProjection> List<X> queryList(OrderListQuery criteria, Class<X> projectionType) {
        return super.queryList(criteria, projectionType);
    }

    @Override
    public <X extends IOrderProjection> PageResult<X> queryPage(
            OrderPageQuery criteria,
            PageRequest pageRequest,
            Class<X> projectionType) {
        return super.queryPage(criteria, pageRequest, projectionType);
    }

    @Override
    public <X extends IOrderProjection> ScrollResult<X> queryScroll(
            OrderPageQuery criteria,
            ScrollPosition cursor,
            int pageSize,
            Class<X> projectionType) {
        return super.queryScroll(criteria, cursor, pageSize, projectionType);
    }

    /**
     * 指定源投影类型与目标投影类型查询单条。
     *
     * <p>先用源投影类型反查其所属源，再以该源发起查询并裁剪到目标投影，
     * 等价于原领域查询契约的 {@code queryById(id, sourceProjection, targetProjection)}。</p>
     *
     * @param id             聚合主键
     * @param sourceProjection 源投影类型（用于反查数据来源，如 {@code OrderEsProjection}）
     * @param projectionType 目标投影类型（查询返回类型）
     * @param <X>            目标投影类型
     * @return 裁剪后的目标投影，未命中返回 {@code null}
     */
    public <X extends IOrderProjection> X queryById(
            Object id,
            Class<?> sourceProjection,
            Class<X> projectionType) {
        return registry().fullProjectionOf(sourceProjection)
                .map(src -> source(src).queryById((Long) id, projectionType))
                .orElse(null);
    }

    /**
     * 按主键读取订单：Redis 优先，未命中回退 ES。
     *
     * <p>编排语义集中在应用层，由框架 {@code fallbackChain} 在 Redis 返回 {@code null} 时自动切换到 ES。</p>
     *
     * @param id            订单主键
     * @param projectionType 目标投影类型
     * @param <X>           目标投影类型
     * @return 命中的投影，两源均未命中返回 {@code null}
     */
    public <X extends IOrderProjection> X getById(Long id, Class<X> projectionType) {
        return fallbackChain(List.of(REDIS_SOURCE, ES_SOURCE))
                .queryById(id, projectionType);
    }

    /**
     * 按主键批量读取订单：Redis 优先，未命中回退 ES。
     *
     * @param ids           订单主键列表
     * @param projectionType 目标投影类型
     * @param <X>           目标投影类型
     * @return 命中的投影列表（逐条按 Redis→ES 回退）
     */
    public <X extends IOrderProjection> List<X> getByIds(List<Long> ids, Class<X> projectionType) {
        return fallbackChain(List.of(REDIS_SOURCE, ES_SOURCE))
                .queryByIds(ids, projectionType);
    }
}
