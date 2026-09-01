package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.IAggregateProjection;
import io.pragmatic.ddd.repository.query.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.IProjectionReducer;
import io.pragmatic.ddd.repository.query.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ProjectionReducerNotFoundException;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单读模型查询的基础设施实现，从投影注册表按型定位检索器与裁剪器并薄薄转发。
 *
 * <p>取数分三跳：先按子投影反查其来源的索引级全量投影（选路），再由检索器从存储
 * 取回该全量形状，最后由裁剪器在 Java 内存中裁剪为调用方指定的子投影。
 * 若调用方要的就是索引级全量投影本身，则跳过裁剪直接返回。</p>
 *
 * <p>本类不持有任何 ES 客户端与字段映射，纯转发到对应 searcher / reducer。</p>
 *
 * @author wizard-lee
 */
@Service
public class OrderQuery implements IOrderQuery {

    private final ProjectorRegistry registry;

    public OrderQuery(ProjectorRegistry registry) {
        this.registry = registry;
    }

    /**
     * 按主键直取单个投影，按需裁剪为目标子投影。
     *
     * @param id 订单号
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 命中的投影，未命中返回 null
     */
    @Override
    public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
        Class<?> sourceType = resolveSourceType(projectionType);
        Class<IAggregateProjection> source = asProjectionClass(sourceType);
        IProjectionByIdSearcher<IAggregateProjection> searcher = registry.getByIdSearcher(source);
        return reduceOne(searcher.getById(id, source), sourceType, projectionType);
    }

    /**
     * 按主键直取单个投影，显式指定取数来源的物理投影类型，再裁剪为目标子投影。
     *
     * @param id 聚合主键
     * @param sourceProjection 取数来源的物理投影类型
     * @param projectionType 目标业务子投影类型
     * @param <X> 目标投影子类型
     * @return 命中的投影，未命中返回 null
     */
    @Override
    public <X extends IOrderProjection> X queryById(Object id, Class<?> sourceProjection, Class<X> projectionType) {
        Class<IAggregateProjection> source = asProjectionClass(sourceProjection);
        IProjectionByIdSearcher<IAggregateProjection> searcher = registry.getByIdSearcher(source);
        return reduceOne(searcher.getById(id, source), sourceProjection, projectionType);
    }

    /**
     * 按批量主键取回投影列表，按需裁剪为目标子投影。
     *
     * @param ids 订单号列表
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 命中的投影列表，无结果返回空列表
     */
    @Override
    public <X extends IOrderProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
        Class<?> sourceType = resolveSourceType(projectionType);
        Class<IAggregateProjection> source = asProjectionClass(sourceType);
        IProjectionByIdSearcher<IAggregateProjection> searcher = registry.getByIdSearcher(source);
        return reduceAll(searcher.getByIds(List.copyOf(ids), source), sourceType, projectionType);
    }

    /**
     * 按条件取回投影列表，按需裁剪为目标子投影。
     *
     * @param query 列表查询条件
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 命中的投影列表，无结果返回空列表
     */
    @Override
    public <X extends IOrderProjection> List<X> queryList(OrderListQuery query, Class<X> projectionType) {
        Class<?> sourceType = resolveSourceType(projectionType);
        Class<IAggregateProjection> source = asProjectionClass(sourceType);
        IProjectionSearcher<OrderListQuery, IAggregateProjection> searcher =
                registry.getSearcher(OrderListQuery.class, source);
        return reduceAll(searcher.search(query, source), sourceType, projectionType);
    }

    /**
     * 按条件取回单个投影，取首条后按需裁剪为目标子投影。
     *
     * @param query 单投影查询条件
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 命中的投影，未命中返回 null
     */
    @Override
    public <X extends IOrderProjection> X queryOne(OrderOneQuery query, Class<X> projectionType) {
        Class<?> sourceType = resolveSourceType(projectionType);
        Class<IAggregateProjection> source = asProjectionClass(sourceType);
        IProjectionSearcher<OrderOneQuery, IAggregateProjection> searcher =
                registry.getSearcher(OrderOneQuery.class, source);
        return searcher.search(query, source).stream()
                .findFirst()
                .map(full -> reduceOne(full, sourceType, projectionType))
                .orElse(null);
    }

    /**
     * 分页查询，分页在检索器侧完成，裁剪只做逐条转换、不改变集合规模。
     *
     * @param query 分页查询条件
     * @param pageRequest 分页请求
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 分页结果，totalCount 取自裁剪前的全量结果
     */
    @Override
    public <X extends IOrderProjection> PageResult<X> queryPage(
            OrderPageQuery query,
            PageRequest pageRequest,
            Class<X> projectionType) {
        Class<?> sourceType = resolveSourceType(projectionType);
        Class<IAggregateProjection> source = asProjectionClass(sourceType);
        IProjectionPagedSearcher<OrderPageQuery, IAggregateProjection> searcher =
                registry.getPagedSearcher(OrderPageQuery.class, source);
        PageResult<IAggregateProjection> fullPage = searcher.searchPage(query, pageRequest, source);
        if (sourceType == projectionType) {
            return castPage(fullPage);
        }
        List<X> data = reduceAll(fullPage.data(), sourceType, projectionType);
        return PageResult.of(data, fullPage.totalCount(), pageRequest);
    }

    /**
     * 滚动查询，滚动在检索器侧完成，裁剪只做逐条转换、不改变集合规模。
     *
     * @param query 滚动查询条件
     * @param cursor 游标位置
     * @param pageSize 每批大小
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 滚动结果
     */
    @Override
    public <X extends IOrderProjection> ScrollResult<X> queryScroll(
            OrderPageQuery query,
            ScrollPosition cursor,
            int pageSize,
            Class<X> projectionType) {
        Class<?> sourceType = resolveSourceType(projectionType);
        Class<IAggregateProjection> source = asProjectionClass(sourceType);
        IProjectionPagedSearcher<OrderPageQuery, IAggregateProjection> searcher =
                registry.getPagedSearcher(OrderPageQuery.class, source);
        ScrollResult<IAggregateProjection> fullScroll = searcher.searchScroll(query, cursor, pageSize, source);
        if (sourceType == projectionType) {
            return castScroll(fullScroll);
        }
        return ScrollResult.of(
                reduceAll(fullScroll.data(), sourceType, projectionType),
                fullScroll.nextCursor());
    }

    /**
     * 解析应取数的索引级全量投影类型：目标类型本身即全量投影时短路返回，
     * 否则按子投影反查其唯一来源。
     *
     * @param projectionType 目标投影类型
     * @return 索引级全量投影类型
     */
    private Class<?> resolveSourceType(Class<?> projectionType) {
        if (registry.isSourceProjection(projectionType)) {
            return projectionType;
        }
        Class<?> sourceType = registry.sourceTypeOf(projectionType);
        if (sourceType == null) {
            throw new ProjectionReducerNotFoundException(
                    "未找到子投影 " + projectionType.getName() + " 对应的裁剪器，未登记其来源投影");
        }
        return sourceType;
    }

    /**
     * 逐条裁剪单个全量投影；目标类型即全量投影时跳过裁剪。
     *
     * @param full 索引级全量投影
     * @param sourceType 全量投影类型，用于短路判断
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 目标投影
     */
    @SuppressWarnings("unchecked")
    private <X extends IOrderProjection> X reduceOne(
            IAggregateProjection full, Class<?> sourceType, Class<X> projectionType) {
        if (full == null) {
            return null;
        }
        if (sourceType == projectionType) {
            return (X) full;
        }
        IProjectionReducer<IAggregateProjection, X> reducer =
                (IProjectionReducer<IAggregateProjection, X>) registry.getReducer(
                        asProjectionClass(sourceType), projectionType);
        return reducer.reduce(full);
    }

    /**
     * 批量裁剪全量投影列表；目标类型即全量投影时跳过裁剪。
     *
     * @param fullList 索引级全量投影列表
     * @param sourceType 全量投影类型，用于短路判断
     * @param projectionType 目标投影类型
     * @param <X> 目标投影子类型
     * @return 目标投影列表
     */
    @SuppressWarnings("unchecked")
    private <X extends IOrderProjection> List<X> reduceAll(
            List<? extends IAggregateProjection> fullList, Class<?> sourceType, Class<X> projectionType) {
        if (sourceType == projectionType) {
            return (List<X>) fullList;
        }
        return fullList.stream()
                .map(full -> reduceOne(full, sourceType, projectionType))
                .toList();
    }

    /**
     * 短路时直接复用检索结果页，避免重建带来的额外拷贝与对象同一性丢失。
     *
     * @param page 检索器返回的全量结果页
     * @param <X> 目标投影子类型
     * @return 同一个结果页实例
     */
    @SuppressWarnings("unchecked")
    private <X extends IOrderProjection> PageResult<X> castPage(PageResult<? extends IAggregateProjection> page) {
        return (PageResult<X>) page;
    }

    /**
     * 短路时直接复用检索结果滚动页，避免重建带来的额外拷贝与对象同一性丢失。
     *
     * @param scroll 检索器返回的全量滚动结果
     * @param <X> 目标投影子类型
     * @return 同一个滚动结果实例
     */
    @SuppressWarnings("unchecked")
    private <X extends IOrderProjection> ScrollResult<X> castScroll(
            ScrollResult<? extends IAggregateProjection> scroll) {
        return (ScrollResult<X>) scroll;
    }

    /**
     * 将通配的投影类型统一视为 {@code Class<IAggregateProjection>}，收敛泛型通配捕获差异。
     *
     * <p>检索器与裁剪器均按运行时 Class 对象精确寻址，此处仅做编译期类型转换，
     * 运行时仍使用同一个 Class 实例，不影响寻址语义。</p>
     *
     * @param projectionType 投影类型
     * @return 按投影根类型表达的同一个 Class 实例
     */
    @SuppressWarnings("unchecked")
    private Class<IAggregateProjection> asProjectionClass(Class<?> projectionType) {
        return (Class<IAggregateProjection>) projectionType;
    }
}
