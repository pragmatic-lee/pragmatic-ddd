package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.repository.query.projection.AbstractAggregateProjector;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderReadService 单元测试：验证合并后的应用服务既承担查询转发，又集中负责 Redis→ES 多源编排。
 *
 * <p>使用真实的 {@link ProjectorRegistry}，登记 Redis 源与 ES 源两份内存副本（均挂载检索器与订单摘要裁剪器），
 * 以手写记录型检索器捕获来源与入参，不连接真实存储，也不依赖 Spring 容器。</p>
 *
 * @author wizard-lee
 */
@DisplayName("OrderReadService 单元测试")
class OrderReadServiceTest {

    private ProjectorRegistry registry;

    private OrderReadService readService;

    private RedisByIdSearcher redisByIdSearcher;

    private EsByIdSearcher esByIdSearcher;

    @BeforeEach
    void setUp() {
        registry = new ProjectorRegistry();
        redisByIdSearcher = new RedisByIdSearcher();
        esByIdSearcher = new EsByIdSearcher();
        registry.register(new OrderSource(
                ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId()), redisByIdSearcher));
        registry.register(new OrderSource(
                ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId()), esByIdSearcher));
        registry.registerDefaultSource(OrderSummaryProjection.class,
                ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId()));
        readService = new OrderReadService(registry);
    }

    // ==================== 多源编排：getById / getByIds ====================

    @Test
    @DisplayName("getById 命中 Redis 时只查询 Redis 源")
    void getById_hitsRedisOnly() {
        redisByIdSearcher.nextResult = fullProjection(1001L, "张三");

        OrderSummaryProjection result = readService.getById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(esByIdSearcher.called).isFalse();
    }

    @Test
    @DisplayName("getById Redis 未命中时回退查询 ES 源")
    void getById_fallsBackToEsWhenRedisMisses() {
        redisByIdSearcher.nextResult = null;
        esByIdSearcher.nextResult = fullProjection(1001L, "张三");

        OrderSummaryProjection result = readService.getById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(redisByIdSearcher.called).isTrue();
        assertThat(esByIdSearcher.called).isTrue();
    }

    @Test
    @DisplayName("getByIds 命中 Redis 源时返回裁剪后的投影列表")
    void getByIds_returnsReducedList() {
        redisByIdSearcher.nextResults =
                List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));

        List<OrderSummaryProjection> results =
                readService.getByIds(List.of(1L, 2L), OrderSummaryProjection.class);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCustomerName()).isEqualTo("张三");
        assertThat(results.get(1).getCustomerName()).isEqualTo("张三");
        assertThat(esByIdSearcher.called).isFalse();
    }

    // ==================== 三跳链路：queryById ====================

    @Test
    @DisplayName("queryById 指定索引级全量投影时短路，直接返回检索结果")
    void queryById_fullProjection_skipsReduction() {
        OrderEsProjection result = readService.queryById(1001L, OrderEsProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1001L);
        assertThat(result.getCustomer()).isNotNull();
    }

    @Test
    @DisplayName("queryById 指定子投影时按默认源裁剪并提升层级")
    void queryById_subProjection_appliesReduction() {
        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1001L);
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getActualAmount()).isEqualTo(8800L);
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("queryById(id, 源投影, 目标投影) 用源投影反查来源并裁剪到目标")
    void queryById_withSourceProjection_resolvesSourceThenReduces() {
        OrderSummaryProjection result =
                readService.queryById(1001L, OrderEsProjection.class, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("queryById 未登记来源的子投影抛出 ProjectionSourceNotFoundException")
    void queryById_unregisteredProjection_throws() {
        assertThatThrownBy(() -> readService.queryById(1L, UnregisteredProjection.class))
                .isInstanceOf(ProjectionSourceNotFoundException.class)
                .hasMessageContaining(UnregisteredProjection.class.getSimpleName());
    }

    // ==================== 条件查询转发 ====================

    @Test
    @DisplayName("queryOne 将条件与投影类型透传给检索器")
    void queryOne_forwardsToSearcher() {
        OrderSummaryProjection result =
                readService.queryOne(new OrderOneQuery.LatestByCustomer(1001L), OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("queryPage 将条件与分页请求透传给分页检索器")
    void queryPage_forwardsToSearcher() {
        PageResult<OrderSummaryProjection> result = readService.queryPage(
                new OrderPageQuery.ByConditions(
                        Optional.of(1001L), Optional.empty(), Optional.empty(),
                        Optional.of("机械键盘"), Optional.of(2001L)),
                PageRequest.of(1, 10),
                OrderSummaryProjection.class);

        assertThat(result.data()).hasSize(2);
        assertThat(result.totalCount()).isEqualTo(2L);
    }

    // ==================== 测试数据与假检索器 ====================

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

    /** 未登记来源的子投影，用于验证选路失败的异常。 */
    private static final class UnregisteredProjection implements IOrderProjection {
    }

    /** 桩「源」：聚合写读一体，绑定各检索器与裁剪器。 */
    private final class OrderSource extends AbstractProjectionSource<Order, OrderEsProjection> {

        private OrderSource(ProjectionSource source, IProjectionByIdSearcher<OrderEsProjection> byIdSearcher) {
            super(source, Order.class, OrderEsProjection.class, new StubProjector(), byIdSearcher);
            bind(new StubOneSearcher());
            bind(new StubListSearcher());
            bind(new StubPagedSearcher());
            bind(new OrderSummaryReducer());
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
        }

        @Override
        public void purge(Object aggregateId) {
        }
    }

    /** 返回 null 的桩投影器，满足源构造约束。 */
    private static final class StubProjector extends AbstractAggregateProjector<Order, OrderEsProjection> {

        private StubProjector() {
            super(OrderEsProjection.class);
        }

        @Override
        public OrderEsProjection project(Order aggregateRoot) {
            return null;
        }
    }

    /** Redis 按主键检索器桩：记录是否被调用，可注入 null 模拟未命中。 */
    private final class RedisByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

        private boolean called;
        private OrderEsProjection nextResult = fullProjection(1001L, "张三");
        private List<OrderEsProjection> nextResults =
                List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));

        @Override
        public OrderEsProjection getById(Object id) {
            this.called = true;
            return nextResult;
        }

        @Override
        public List<OrderEsProjection> getByIds(List<Object> ids) {
            this.called = true;
            return nextResults;
        }
    }

    /** ES 按主键检索器桩：记录是否被调用，总返回数据。 */
    private final class EsByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

        private boolean called;
        private OrderEsProjection nextResult = fullProjection(1001L, "张三");
        private List<OrderEsProjection> nextResults =
                List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));

        @Override
        public OrderEsProjection getById(Object id) {
            this.called = true;
            return nextResult;
        }

        @Override
        public List<OrderEsProjection> getByIds(List<Object> ids) {
            this.called = true;
            return nextResults;
        }
    }

    /** 单投影检索器桩：客户 ID 为 1001 时命中。 */
    private final class StubOneSearcher implements IProjectionSearcher<OrderOneQuery, OrderEsProjection> {

        @Override
        public Class<OrderOneQuery> criteriaType() {
            return OrderOneQuery.class;
        }

        @Override
        public List<OrderEsProjection> search(OrderOneQuery condition) {
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
        public List<OrderEsProjection> search(OrderListQuery condition) {
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
        public PageResult<OrderEsProjection> searchPage(OrderPageQuery condition, PageRequest pageRequest) {
            List<OrderEsProjection> data = List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));
            return PageResult.of(data, 2L, pageRequest);
        }

        @Override
        public ScrollResult<OrderEsProjection> searchScroll(
                OrderPageQuery condition, ScrollPosition cursor, int pageSize) {
            List<OrderEsProjection> data = List.of(fullProjection(1L, "张三"), fullProjection(2L, "张三"));
            return ScrollResult.of(data, "cursor-2");
        }
    }
}
