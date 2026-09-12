package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.example.order.domain.order.projection.IOrderESSource;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderRedisSource;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderCacheSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.repository.query.exception.ProjectionReducerNotFoundException;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderReadService 单元测试：验证注入领域源接口后的按族路由与裁剪行为。
 *
 * <p>ById 族走 Redis 源接口、One/List/Page 族走 ES 源接口；用桩实现记录调用情况，
 * 不连接真实存储、不依赖 Spring 容器。</p>
 *
 * @author wizard-lee
 */
@DisplayName("OrderReadService 单元测试")
class OrderReadServiceTest {

    private OrderReadService readService;

    private StubRedisSource redisSource;

    private StubEsSource esSource;

    @BeforeEach
    void setUp() {
        redisSource = new StubRedisSource();
        esSource = new StubEsSource();
        readService = new OrderReadService(redisSource, esSource);
    }

    // ==================== ById 族：走 Redis ====================

    @Test
    @DisplayName("queryById 走 Redis 源并裁剪为子投影")
    void queryById_routesToRedis() {
        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1001L);
        assertThat(result.getActualAmount()).isEqualByComparingTo("88.00");
        assertThat(result.getCustomerName()).isEqualTo("张三");
        assertThat(redisSource.getByIdCalled).isTrue();
    }

    @Test
    @DisplayName("queryById 目标即全量投影时不做裁剪")
    void queryById_fullProjection_shortCircuits() {
        OrderCacheProjection result = readService.queryById(1001L, OrderCacheProjection.class);

        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("queryById 缓存未命中返回 null")
    void queryById_miss_returnsNull() {
        redisSource.next = null;

        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("queryById 源未注册裁剪器时抛 ProjectionReducerNotFoundException")
    void queryById_unregisteredReducer_throws() {
        assertThatThrownBy(() -> readService.queryById(1001L, UnregisteredProjection.class))
                .isInstanceOf(ProjectionReducerNotFoundException.class)
                .hasMessageContaining(UnregisteredProjection.class.getName());
    }

    @Test
    @DisplayName("queryByIds 走 Redis 源并裁剪")
    void queryByIds_routesToRedis() {
        redisSource.nextMany = List.of(fullCacheProjection(1L), fullCacheProjection(2L));

        List<OrderSummaryProjection> results = readService.queryByIds(List.of(1L, 2L), OrderSummaryProjection.class);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCustomerName()).isEqualTo("张三");
        assertThat(redisSource.getByIdsCalled).isTrue();
    }

    // ==================== One / List / Page 族：走 ES ====================

    @Test
    @DisplayName("queryOne 走 ES 源并裁剪")
    void queryOne_routesToEs() {
        List<OrderSummaryProjection> result =
                readService.queryOne(new OrderOneQuery.LatestByCustomer(1001L), OrderSummaryProjection.class);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerName()).isEqualTo("张三");
        assertThat(esSource.searchOneCalled).isTrue();
    }

    @Test
    @DisplayName("queryList 走 ES 源并裁剪")
    void queryList_routesToEs() {
        List<OrderSummaryProjection> result = readService.queryList(
                new OrderListQuery.TopRecent(1001L, 2, 10), OrderSummaryProjection.class);

        assertThat(result).hasSize(2);
        assertThat(esSource.searchListCalled).isTrue();
    }

    @Test
    @DisplayName("queryPage 走 ES 源并裁剪，totalCount 取裁剪前总量")
    void queryPage_routesToEs() {
        PageResult<OrderSummaryProjection> result = readService.queryPage(
                new OrderPageQuery.ByConditions(
                        Optional.of(1001L), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(2001L), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty()),
                PageRequest.of(1, 10),
                OrderSummaryProjection.class);

        assertThat(result.data()).hasSize(2);
        assertThat(result.totalCount()).isEqualTo(2L);
        assertThat(esSource.searchPageCalled).isTrue();
    }

    // ==================== 测试数据 ====================

    private static OrderEsProjection fullEsProjection(Long id) {
        OrderEsProjection full = new OrderEsProjection();
        full.setOrderId(id);
        full.setStatus(2);
        full.setStatusName("PAID");
        full.setActualAmount(new BigDecimal("88.00"));
        OrderEsProjection.CustomerProjection customer = new OrderEsProjection.CustomerProjection();
        customer.setCustomerId(1001L);
        customer.setCustomerName("张三");
        full.setCustomer(customer);
        return full;
    }

    private static OrderCacheProjection fullCacheProjection(Long id) {
        OrderCacheProjection full = new OrderCacheProjection();
        full.setOrderId(id);
        full.setStatus(2);
        full.setStatusName("PAID");
        full.setActualAmount(new BigDecimal("88.00"));
        OrderCacheProjection.CustomerProjection customer = new OrderCacheProjection.CustomerProjection();
        customer.setCustomerId(1001L);
        customer.setCustomerName("张三");
        full.setCustomer(customer);
        full.setVersion(1L);
        return full;
    }

    /** 未在任何源注册的子投影，用于验证裁剪器缺失的异常。 */
    private static final class UnregisteredProjection implements IOrderProjection {
    }

    /** Redis 源桩：仅实现 ById 族 + 缓存投影裁剪器，记录调用情况。 */
    private static final class StubRedisSource implements IOrderRedisSource {

        private boolean getByIdCalled;
        private boolean getByIdsCalled;
        private OrderCacheProjection next = fullCacheProjection(1001L);
        private List<OrderCacheProjection> nextMany =
                List.of(fullCacheProjection(1L), fullCacheProjection(2L));
        private final IReducer<OrderCacheProjection, OrderSummaryProjection> reducer = new OrderCacheSummaryReducer();

        @Override
        public OrderCacheProjection getById(Object id) {
            this.getByIdCalled = true;
            return next;
        }

        @Override
        public List<OrderCacheProjection> getByIds(List<Object> ids) {
            this.getByIdsCalled = true;
            return nextMany;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <X extends IAggregateProjection> IReducer<OrderCacheProjection, X> getReducer(Class<X> target) {
            if (target.isAssignableFrom(reducer.projectionType())) {
                return (IReducer<OrderCacheProjection, X>) reducer;
            }
            return null;
        }
    }

    /** ES 源桩：实现四族 + 概要裁剪器，记录调用情况。 */
    private static final class StubEsSource implements IOrderESSource {

        private boolean searchOneCalled;
        private boolean searchListCalled;
        private boolean searchPageCalled;
        private final IReducer<OrderEsProjection, OrderSummaryProjection> reducer = new OrderSummaryReducer();

        @Override
        public OrderEsProjection getById(Object id) {
            return fullEsProjection(1001L);
        }

        @Override
        public List<OrderEsProjection> getByIds(List<Object> ids) {
            return ids.stream().map(id -> fullEsProjection((Long) id)).toList();
        }

        @Override
        public List<OrderEsProjection> search(OrderOneQuery criteria) {
            this.searchOneCalled = true;
            return List.of(fullEsProjection(1L));
        }

        @Override
        public List<OrderEsProjection> search(OrderListQuery criteria) {
            this.searchListCalled = true;
            return List.of(fullEsProjection(1L), fullEsProjection(2L));
        }

        @Override
        public PageResult<OrderEsProjection> searchPage(OrderPageQuery criteria, PageRequest pageRequest) {
            this.searchPageCalled = true;
            return PageResult.of(List.of(fullEsProjection(1L), fullEsProjection(2L)), 2L, pageRequest);
        }

        @Override
        public ScrollResult<OrderEsProjection> searchScroll(
                OrderPageQuery criteria, ScrollPosition cursor, int pageSize) {
            return ScrollResult.of(List.of(fullEsProjection(1L)), "cursor-1");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <X extends IAggregateProjection> IReducer<OrderEsProjection, X> getReducer(Class<X> target) {
            if (target.isAssignableFrom(reducer.projectionType())) {
                return (IReducer<OrderEsProjection, X>) reducer;
            }
            return null;
        }
    }
}
