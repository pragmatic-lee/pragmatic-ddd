package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.application.IQueryApplicationService;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderESSource;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderRedisSource;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.exception.ProjectionReducerNotFoundException;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单读侧应用服务：注入领域层源接口（端口），按查询族直接调用，不依赖基础设施具体实现。
 *
 * <p>选源规则（简版，无回退）：ById 族走 Redis 缓存副本（{@link IOrderRedisSource}，承载
 * {@code OrderCacheProjection}）；One / List / Page 族走 ES 索引（{@link IOrderESSource}，
 * 承载 {@code OrderEsProjection}）。调用方只传目标投影类型，不感知源。</p>
 *
 * @author wizard-lee
 */
@Service
public class OrderReadService implements IQueryApplicationService {

    private final IOrderRedisSource redisSource;
    private final IOrderESSource esSource;

    public OrderReadService(IOrderRedisSource redisSource, IOrderESSource esSource) {
        this.redisSource = redisSource;
        this.esSource = esSource;
    }

    /**
     * 按主键取投影（走 Redis 缓存副本）。
     *
     * @param id 订单号
     * @param projectionType 目标投影类型（须为 Redis 源可提供者）
     * @param <X> 目标投影类型
     * @return 投影；缓存未命中返回 null
     */
    public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
        var full = redisSource.getById(id);
        if (full == null) {
            return null;
        }
        return reduceWith(redisSource.getReducer(projectionType), full, projectionType);
    }

    /**
     * 按主键批量取投影（走 Redis 缓存副本）。
     *
     * @param ids 订单号列表
     * @param projectionType 目标投影类型
     * @param <X> 目标投影类型
     * @return 投影列表
     */
    public <X extends IOrderProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
        var reducer = redisSource.getReducer(projectionType);
        return redisSource.getByIds(List.copyOf(ids)).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }

    /**
     * 单条条件查询（走 ES 索引）。
     *
     * @param criteria 业务条件
     * @param projectionType 目标投影类型
     * @param <X> 目标投影类型
     * @return 投影列表
     */
    public <X extends IOrderProjection> List<X> queryOne(OrderOneQuery criteria, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        return esSource.search(criteria).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }

    /**
     * 列表条件查询（走 ES 索引）。
     *
     * @param criteria 业务条件
     * @param projectionType 目标投影类型
     * @param <X> 目标投影类型
     * @return 投影列表
     */
    public <X extends IOrderProjection> List<X> queryList(OrderListQuery criteria, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        return esSource.search(criteria).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }

    /**
     * 分页查询（走 ES 索引）。
     *
     * @param criteria 业务条件
     * @param pageRequest 分页请求
     * @param projectionType 目标投影类型
     * @param <X> 目标投影类型
     * @return 结果页
     */
    public <X extends IOrderProjection> PageResult<X> queryPage(
            OrderPageQuery criteria, PageRequest pageRequest, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        PageResult<OrderEsProjection> result = esSource.searchPage(criteria, pageRequest);
        List<X> reduced = result.data().stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
        return PageResult.of(reduced, result.totalCount(), pageRequest);
    }

    private <S extends IOrderProjection, X extends IOrderProjection> X reduceWith(
            IReducer<S, X> reducer, S full, Class<X> projectionType) {
        if (projectionType.isInstance(full)) {
            return projectionType.cast(full);
        }
        if (reducer == null) {
            throw new ProjectionReducerNotFoundException("源未注册裁剪器: " + projectionType.getName());
        }
        return reducer.reduce(full);
    }
}
