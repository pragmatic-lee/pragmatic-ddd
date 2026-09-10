package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderCacheSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.AbstractAggregateProjector;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderReadService 单元测试：验证读服务内置回源链后的选路与裁剪行为。
 *
 * <p>使用真实的 {@link ProjectorRegistry}，按生产配置登记两份异构副本：
 * Redis 源承载 {@link OrderCacheProjection} 且仅绑定按主键检索器，ES 源承载 {@link OrderEsProjection}
 * 且绑定全部检索器。以手写记录型检索器捕获来源与入参，不连接真实存储，也不依赖 Spring 容器。</p>
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
        registry.register(new RedisSource(
                ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId()), redisByIdSearcher));
        registry.register(new EsSource(
                ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId()), esByIdSearcher));
        registry.registerDefaultSource(OrderSummaryProjection.class,
                ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId()));
        readService = new OrderReadService(registry);
    }

    // ==================== 内置回源链：Redis 优先，未命中回退 ES ====================

    @Test
    @DisplayName("按主键查询命中 Redis 时只查询 Redis 源")
    void queryById_hitsRedisOnly() {
        redisByIdSearcher.nextResult = fullCacheProjection(1001L, "张三");

        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(esByIdSearcher.called).isFalse();
    }

    @Test
    @DisplayName("按主键查询 Redis 未命中时回退 ES 源")
    void queryById_fallsBackToEsWhenRedisMisses() {
        redisByIdSearcher.nextResult = null;
        esByIdSearcher.nextResult = fullEsProjection(1001L, "张三");

        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(redisByIdSearcher.called).isTrue();
        assertThat(esByIdSearcher.called).isTrue();
    }

    @Test
    @DisplayName("按主键查询子投影时完成字段裁剪与层级提升")
    void queryById_subProjection_appliesReduction() {
        redisByIdSearcher.nextResult = fullCacheProjection(1001L, "张三");

        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1001L);
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getActualAmount()).isEqualByComparingTo("88.00");
        assertThat(result.getCustomerName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("批量按主键查询命中 Redis 时返回裁剪后的投影列表")
    void queryByIds_returnsReducedList() {
        redisByIdSearcher.nextResults =
                List.of(fullCacheProjection(1L, "张三"), fullCacheProjection(2L, "张三"));

        List<OrderSummaryProjection> results =
                readService.queryByIds(List.of(1L, 2L), OrderSummaryProjection.class);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCustomerName()).isEqualTo("张三");
        assertThat(results.get(1).getCustomerName()).isEqualTo("张三");
        assertThat(esByIdSearcher.called).isFalse();
    }

    @Test
    @DisplayName("批量按主键查询 Redis 整批未命中时整批回退 ES")
    void queryByIds_fallsBackToEsWhenRedisMisses() {
        redisByIdSearcher.nextResults = List.of();
        esByIdSearcher.nextResults =
                List.of(fullEsProjection(1L, "张三"), fullEsProjection(2L, "张三"));

        List<OrderSummaryProjection> results =
                readService.queryByIds(List.of(1L, 2L), OrderSummaryProjection.class);

        assertThat(results).hasSize(2);
        assertThat(redisByIdSearcher.called).isTrue();
        assertThat(esByIdSearcher.called).isTrue();
    }

    // ==================== 能力过滤：链上源不支持时自动跳过 ====================

    @Test
    @DisplayName("查询 ES 全量投影时跳过不承载该投影的 Redis 源")
    void queryById_fullProjection_skipsUnsupportedSource() {
        OrderEsProjection result = readService.queryById(1001L, OrderEsProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1001L);
        assertThat(result.getCustomer()).isNotNull();
        assertThat(redisByIdSearcher.called).isFalse();
        assertThat(esByIdSearcher.called).isTrue();
    }

    @Test
    @DisplayName("链上无源可提供该投影时抛出 ProjectionSourceNotFoundException")
    void queryById_unregisteredProjection_throws() {
        assertThatThrownBy(() -> readService.queryById(1L, UnregisteredProjection.class))
                .isInstanceOf(ProjectionSourceNotFoundException.class)
                .hasMessageContaining(UnregisteredProjection.class.getSimpleName());
    }

    // ==================== 条件查询：Redis 无检索器，自动落到 ES ====================

    @Test
    @DisplayName("queryOne 跳过无单条检索器的 Redis 源，落到 ES")
    void queryOne_forwardsToSearcher() {
        OrderSummaryProjection result =
                readService.queryOne(new OrderOneQuery.LatestByCustomer(1001L), OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(redisByIdSearcher.called).isFalse();
    }

    @Test
    @DisplayName("queryPage 跳过无分页检索器的 Redis 源，落到 ES")
    void queryPage_forwardsToSearcher() {
        PageResult<OrderSummaryProjection> result = readService.queryPage(
                new OrderPageQuery.ByConditions(
                        Optional.of(1001L), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(2001L), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of("机械键盘")),
                PageRequest.of(1, 10),
                OrderSummaryProjection.class);

        assertThat(result.data()).hasSize(2);
        assertThat(result.totalCount()).isEqualTo(2L);
        assertThat(redisByIdSearcher.called).isFalse();
    }

    // ==================== 测试数据与假检索器 ====================

    private static OrderEsProjection fullEsProjection(Long id, String customerName) {
        OrderEsProjection full = new OrderEsProjection();
        full.setOrderId(id);
        full.setStatus(2);
        full.setStatusName("PAID");
        full.setActualAmount(new BigDecimal("88.00"));
        OrderEsProjection.CustomerProjection customer = new OrderEsProjection.CustomerProjection();
        customer.setCustomerId(1001L);
        customer.setCustomerName(customerName);
        full.setCustomer(customer);
        return full;
    }

    private static OrderCacheProjection fullCacheProjection(Long id, String customerName) {
        OrderCacheProjection full = new OrderCacheProjection();
        full.setOrderId(id);
        full.setStatus(2);
        full.setStatusName("PAID");
        full.setActualAmount(new BigDecimal("88.00"));
        OrderCacheProjection.CustomerProjection customer = new OrderCacheProjection.CustomerProjection();
        customer.setCustomerId(1001L);
        customer.setCustomerName(customerName);
        full.setCustomer(customer);
        full.setVersion(1L);
        return full;
    }

    /** 未登记来源的子投影，用于验证选路失败的异常。 */
    private static final class UnregisteredProjection implements IOrderProjection {
    }

    /** Redis 缓存副本源：仅承载缓存投影与按主键检索器，无检索器。 */
    private static final class RedisSource extends AbstractProjectionSource<Order, OrderCacheProjection> {

        private RedisSource(
                ProjectionSource source, IProjectionByIdSearcher<OrderCacheProjection> byIdSearcher) {
            super(source, Order.class, OrderCacheProjection.class, new StubCacheProjector(), byIdSearcher);
            bind(new OrderCacheSummaryReducer());
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
        }

        @Override
        public void purge(Object aggregateId) {
        }
    }

    /** ES 索引源：承载索引级全量投影与全部检索器。 */
    private static final class EsSource extends AbstractProjectionSource<Order, OrderEsProjection> {

        private EsSource(ProjectionSource source, IProjectionByIdSearcher<OrderEsProjection> byIdSearcher) {
            super(source, Order.class, OrderEsProjection.class, new StubEsProjector(), byIdSearcher);
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

    /** 返回 null 的桩投影器，满足源构造约束（ES 源）。 */
    private static final class StubEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> {

        private StubEsProjector() {
            super(OrderEsProjection.class);
        }

        @Override
        public OrderEsProjection project(Order aggregateRoot) {
            return null;
        }
    }

    /** 返回 null 的桩投影器，满足源构造约束（Redis 源）。 */
    private static final class StubCacheProjector extends AbstractAggregateProjector<Order, OrderCacheProjection> {

        private StubCacheProjector() {
            super(OrderCacheProjection.class);
        }

        @Override
        public OrderCacheProjection project(Order aggregateRoot) {
            return null;
        }
    }

    /** Redis 按主键检索器桩：记录是否被调用，可注入结果模拟命中 / 未命中。 */
    private static final class RedisByIdSearcher implements IProjectionByIdSearcher<OrderCacheProjection> {

        private boolean called;
        private OrderCacheProjection nextResult = fullCacheProjection(1001L, "张三");
        private List<OrderCacheProjection> nextResults =
                List.of(fullCacheProjection(1L, "张三"), fullCacheProjection(2L, "张三"));

        @Override
        public OrderCacheProjection getById(Object id) {
            this.called = true;
            return nextResult;
        }

        @Override
        public List<OrderCacheProjection> getByIds(List<Object> ids) {
            this.called = true;
            return nextResults;
        }
    }

    /** ES 按主键检索器桩：记录是否被调用，总返回数据。 */
    private static final class EsByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

        private boolean called;
        private OrderEsProjection nextResult = fullEsProjection(1001L, "张三");
        private List<OrderEsProjection> nextResults =
                List.of(fullEsProjection(1L, "张三"), fullEsProjection(2L, "张三"));

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
    private static final class StubOneSearcher implements IProjectionSearcher<OrderOneQuery, OrderEsProjection> {

        @Override
        public Class<OrderOneQuery> criteriaType() {
            return OrderOneQuery.class;
        }

        @Override
        public List<OrderEsProjection> search(OrderOneQuery condition) {
            if (condition instanceof OrderOneQuery.LatestByCustomer c
                    && Long.valueOf(1001L).equals(c.customerId())) {
                return List.of(fullEsProjection(1L, "张三"));
            }
            return List.of();
        }
    }

    /** 列表检索器桩：返回两份全量投影。 */
    private static final class StubListSearcher implements IProjectionSearcher<OrderListQuery, OrderEsProjection> {

        @Override
        public Class<OrderListQuery> criteriaType() {
            return OrderListQuery.class;
        }

        @Override
        public List<OrderEsProjection> search(OrderListQuery condition) {
            return List.of(fullEsProjection(1L, "张三"), fullEsProjection(2L, "张三"));
        }
    }

    /** 分页 / 滚动检索器桩：固定返回两页数据与游标。 */
    private static final class StubPagedSearcher
            implements IProjectionPagedSearcher<OrderPageQuery, OrderEsProjection> {

        @Override
        public Class<OrderPageQuery> criteriaType() {
            return OrderPageQuery.class;
        }

        @Override
        public PageResult<OrderEsProjection> searchPage(OrderPageQuery condition, PageRequest pageRequest) {
            List<OrderEsProjection> data = List.of(fullEsProjection(1L, "张三"), fullEsProjection(2L, "张三"));
            return PageResult.of(data, 2L, pageRequest);
        }

        @Override
        public ScrollResult<OrderEsProjection> searchScroll(
                OrderPageQuery condition, ScrollPosition cursor, int pageSize) {
            List<OrderEsProjection> data = List.of(fullEsProjection(1L, "张三"), fullEsProjection(2L, "张三"));
            return ScrollResult.of(data, "cursor-2");
        }
    }
}
