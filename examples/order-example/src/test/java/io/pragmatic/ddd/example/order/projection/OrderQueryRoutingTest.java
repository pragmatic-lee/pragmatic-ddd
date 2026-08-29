package io.pragmatic.ddd.example.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.reducer.IOrderSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.repository.query.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ProjectionReducerNotFoundException;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 OrderQuery 读侧三跳链路：按子投影反查来源 → 检索器取全量 → 裁剪器内存裁剪。
 *
 * <p>以测试桩替代 ES 客户端，只验证门面的选路、短路与裁剪编排，不依赖真实存储。</p>
 *
 * @author wizard-lee
 */
class OrderQueryRoutingTest {

    private ProjectorRegistry registry;

    private OrderQuery orderQuery;

    @BeforeEach
    void setUp() {
        registry = new ProjectorRegistry();
        registry.register(new StubByIdSearcher());
        registry.register(new StubOneSearcher());
        registry.register(new StubListSearcher());
        registry.register(new StubPagedSearcher());
        registry.markSourceProjection(OrderEsProjection.class);
        // 面向领域契约登记，具体实现由基础设施层提供
        IOrderSummaryReducer summaryReducer = new OrderSummaryReducer();
        registry.register(summaryReducer);
        orderQuery = new OrderQuery(registry);
    }

    /** 构造一份索引级全量投影，客户名位于嵌套结构中。 */
    private OrderEsProjection fullProjection(Long id, String customerName) {
        OrderEsProjection full = new OrderEsProjection();
        full.setOrderId(id);
        full.setStatus(2);
        full.setStatusName("PAID");
        full.setActualAmount(8800L);
        OrderEsProjection.CustomerProjection customer = new OrderEsProjection.CustomerProjection();
        customer.setCustomerId(1001L);
        customer.setCustomerName(customerName);
        full.setCustomer(customer);
        return full;
    }

    @Test
    void queryById_sourceProjection_skipsReduction() {
        OrderEsProjection result = orderQuery.queryById(1L, OrderEsProjection.class);

        // 短路：目标即索引级全量投影，直接返回检索结果，不经过裁剪器
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getCustomer()).isNotNull();
    }

    @Test
    void queryById_subProjection_appliesReductionWithLevelPromotion() {
        OrderSummaryProjection result = orderQuery.queryById(1L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getActualAmount()).isEqualTo(8800L);
        // 层级提升：ES 文档为 customer.customerName，概要投影为顶层 customerName
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    void queryByIds_subProjection_reducesEachElement() {
        List<OrderSummaryProjection> results =
                orderQuery.queryByIds(List.of(1L, 2L), OrderSummaryProjection.class);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(OrderSummaryProjection::getCustomerName)
                .containsExactly("张三", "张三");
    }

    @Test
    void queryOne_miss_returnsNull() {
        OrderSummaryProjection result =
                orderQuery.queryOne(new OrderOneQuery.LatestByCustomer(9999L), OrderSummaryProjection.class);

        assertThat(result).isNull();
    }

    @Test
    void queryOne_hit_returnsReducedProjection() {
        OrderSummaryProjection result =
                orderQuery.queryOne(new OrderOneQuery.LatestByCustomer(1001L), OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    void queryList_subProjection_reducesAllElements() {
        List<OrderSummaryProjection> results =
                orderQuery.queryList(new OrderListQuery.TopRecent(1001L, 2, 10), OrderSummaryProjection.class);

        assertThat(results).hasSize(2);
    }

    @Test
    void queryPage_totalCountComesFromPreReductionResult() {
        PageResult<OrderSummaryProjection> page = orderQuery.queryPage(
                new OrderPageQuery.ByConditions(
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()),
                PageRequest.of(1, 10),
                OrderSummaryProjection.class);

        // 分页在检索器侧完成，裁剪只做逐条转换；totalCount 取自裁剪前的全量结果
        assertThat(page.data()).hasSize(2);
        assertThat(page.totalCount()).isEqualTo(2L);
        assertThat(page.request().pageNumber()).isEqualTo(1);
    }

    @Test
    void queryScroll_returnsCursorFromPreReductionResult() {
        ScrollResult<OrderSummaryProjection> scroll = orderQuery.queryScroll(
                new OrderPageQuery.ByConditions(
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()),
                ScrollPosition.initial(),
                10,
                OrderSummaryProjection.class);

        assertThat(scroll.data()).hasSize(2);
        assertThat(scroll.nextCursor()).isEqualTo("cursor-2");
    }

    @Test
    void query_unregisteredSubProjection_throwsReducerNotFound() {
        // UnregisteredProjection 既非索引级全量投影，也未登记裁剪器来源
        assertThatThrownBy(() -> orderQuery.queryById(1L, UnregisteredProjection.class))
                .isInstanceOf(ProjectionReducerNotFoundException.class)
                .hasMessageContaining(UnregisteredProjection.class.getName());
    }

    /** 未登记来源的子投影，用于验证选路失败时的异常。 */
    private static final class UnregisteredProjection implements
            io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection {
    }

    /** 按主键检索器桩：返回两份全量投影。 */
    private final class StubByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

        @Override
        public Class<OrderEsProjection> projectionType() {
            return OrderEsProjection.class;
        }

        @Override
        public OrderEsProjection getById(Object id, Class<OrderEsProjection> projectionType) {
            return fullProjection((Long) id, "张三");
        }

        @Override
        public List<OrderEsProjection> getByIds(List<Object> ids, Class<OrderEsProjection> projectionType) {
            return ids.stream().map(id -> fullProjection((Long) id, "张三")).toList();
        }
    }

    /** 单投影检索器桩：客户 ID 为 1001 时命中。 */
    private final class StubOneSearcher implements IProjectionSearcher<OrderOneQuery, OrderEsProjection> {

        @Override
        public Class<OrderOneQuery> criteriaType() {
            return OrderOneQuery.class;
        }

        @Override
        public Class<OrderEsProjection> projectionType() {
            return OrderEsProjection.class;
        }

        @Override
        public List<OrderEsProjection> search(OrderOneQuery condition, Class<OrderEsProjection> projectionType) {
            if (condition instanceof OrderOneQuery.LatestByCustomer c
                    && Long.valueOf(1001L).equals(c.customerId())) {
                return List.of(fullProjection(1L, "张三"));
            }
            return List.of();
        }
    }

    /** 列表检索器桩：返回两份全量投影。 */
    private final class StubListSearcher implements IProjectionSearcher<OrderListQuery, OrderEsProjection> {

        @Override
        public Class<OrderListQuery> criteriaType() {
            return OrderListQuery.class;
        }

        @Override
        public Class<OrderEsProjection> projectionType() {
            return OrderEsProjection.class;
        }

        @Override
        public List<OrderEsProjection> search(OrderListQuery condition, Class<OrderEsProjection> projectionType) {
            return List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));
        }
    }

    /** 分页 / 滚动检索器桩：固定返回两页数据与游标。 */
    private final class StubPagedSearcher implements IProjectionPagedSearcher<OrderPageQuery, OrderEsProjection> {

        @Override
        public Class<OrderPageQuery> criteriaType() {
            return OrderPageQuery.class;
        }

        @Override
        public Class<OrderEsProjection> projectionType() {
            return OrderEsProjection.class;
        }

        @Override
        public PageResult<OrderEsProjection> searchPage(
                OrderPageQuery condition, PageRequest pageRequest, Class<OrderEsProjection> projectionType) {
            List<OrderEsProjection> data = List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));
            return PageResult.of(data, 2L, pageRequest);
        }

        @Override
        public ScrollResult<OrderEsProjection> searchScroll(
                OrderPageQuery condition, ScrollPosition cursor, int pageSize,
                Class<OrderEsProjection> projectionType) {
            List<OrderEsProjection> data = List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));
            return ScrollResult.of(data, "cursor-2");
        }
    }
}
