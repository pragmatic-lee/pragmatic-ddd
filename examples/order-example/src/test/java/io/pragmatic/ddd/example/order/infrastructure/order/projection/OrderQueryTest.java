package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ProjectionReducerNotFoundException;
import io.pragmatic.ddd.repository.query.ProjectionSearcherNotFoundException;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OrderQuery 纯单元测试。
 *
 * <p>OrderQuery 只做「按型定位检索器 + 转发」，因此使用真实的 {@link ProjectorRegistry}，
 * 注册手写记录型假检索器捕获入参，不连接 ES，也不依赖 Spring 容器。
 * 覆盖 queryById / queryByIds / queryList / queryOne / queryPage / queryScroll 的转发语义，
 * 以及检索器未注册时抛出 {@link ProjectionSearcherNotFoundException} 的兜底行为。</p>
 *
 * @author wizard-lee
 */
@DisplayName("OrderQuery 单元测试")
class OrderQueryTest {

    private ProjectorRegistry registry;

    private RecordingByIdSearcher byIdSearcher;

    private RecordingListSearcher listSearcher;

    private RecordingOneSearcher oneSearcher;

    private RecordingPagedSearcher pagedSearcher;

    private OrderQuery query;

    @BeforeEach
    void setUp() {
        registry = new ProjectorRegistry();
        byIdSearcher = new RecordingByIdSearcher();
        listSearcher = new RecordingListSearcher();
        oneSearcher = new RecordingOneSearcher();
        pagedSearcher = new RecordingPagedSearcher();
        registry.register(byIdSearcher);
        registry.register(listSearcher);
        registry.register(oneSearcher);
        registry.register(pagedSearcher);
        // 本组用例查询的即索引级全量投影，需先标记来源，门面方可短路直取
        registry.markSourceProjection(OrderEsProjection.class);
        query = new OrderQuery(registry);
    }

    // ==================== queryById / queryByIds ====================

    @Test
    @DisplayName("queryById 将主键与投影类型透传给按主键检索器并返回其结果")
    void queryById_forwardsToByIdSearcher() {
        OrderEsProjection expected = projection(1001L);
        byIdSearcher.nextResult = expected;

        OrderEsProjection result = query.queryById(1001L, OrderEsProjection.class);

        assertThat(result).isSameAs(expected);
        assertThat(byIdSearcher.capturedId).isEqualTo(1001L);
        assertThat(byIdSearcher.capturedType).isEqualTo(OrderEsProjection.class);
    }

    @Test
    @DisplayName("queryByIds 将 Long 主键列表转为对象列表透传给按主键检索器")
    void queryByIds_forwardsIdsAsObjectList() {
        List<OrderEsProjection> expected = List.of(projection(1L), projection(2L));
        byIdSearcher.nextResults = expected;

        List<OrderEsProjection> result = query.queryByIds(List.of(1L, 2L), OrderEsProjection.class);

        assertThat(result).isEqualTo(expected);
        assertThat(byIdSearcher.capturedIds).containsExactly(1L, 2L);
    }

    // ==================== queryList / queryOne ====================

    @Test
    @DisplayName("queryList 按 (OrderListQuery, 投影类型) 定位列表检索器并透传条件")
    void queryList_forwardsToSearcher() {
        OrderListQuery criteria = new OrderListQuery.TopByAmount(10, 1, 2001L);
        List<OrderEsProjection> expected = List.of(projection(1L));
        listSearcher.nextResults = expected;

        List<OrderEsProjection> result = query.queryList(criteria, OrderEsProjection.class);

        assertThat(result).isEqualTo(expected);
        assertThat(listSearcher.capturedCriteria).isSameAs(criteria);
        assertThat(listSearcher.capturedType).isEqualTo(OrderEsProjection.class);
    }

    @Test
    @DisplayName("queryOne 命中多条时返回检索结果的第一条")
    void queryOne_returnsFirstResult() {
        OrderOneQuery criteria = new OrderOneQuery.LatestByCustomer(2001L);
        OrderEsProjection first = projection(1L);
        oneSearcher.nextResults = List.of(first, projection(2L));

        OrderEsProjection result = query.queryOne(criteria, OrderEsProjection.class);

        assertThat(result).isSameAs(first);
        assertThat(oneSearcher.capturedCriteria).isSameAs(criteria);
    }

    @Test
    @DisplayName("queryOne 检索结果为空时返回 null")
    void queryOne_returnsNullWhenEmpty() {
        oneSearcher.nextResults = List.of();

        OrderEsProjection result = query.queryOne(new OrderOneQuery.LatestByCustomer(2001L), OrderEsProjection.class);

        assertThat(result).isNull();
    }

    // ==================== queryPage / queryScroll ====================

    @Test
    @DisplayName("queryPage 将条件与分页请求透传给分页检索器并回传结果页")
    void queryPage_forwardsToPagedSearcher() {
        OrderPageQuery criteria = anyPageQuery();
        PageRequest pageRequest = PageRequest.of(2, 20);
        PageResult<OrderEsProjection> expected =
                PageResult.of(List.of(projection(1L)), 42L, pageRequest);
        pagedSearcher.nextPageResult = expected;

        PageResult<OrderEsProjection> result = query.queryPage(criteria, pageRequest, OrderEsProjection.class);

        assertThat(result).isSameAs(expected);
        assertThat(pagedSearcher.capturedCriteria).isSameAs(criteria);
        assertThat(pagedSearcher.capturedPageRequest).isSameAs(pageRequest);
    }

    @Test
    @DisplayName("queryScroll 将条件、游标与页大小透传给分页检索器并回传滚动结果")
    void queryScroll_forwardsToPagedSearcher() {
        OrderPageQuery criteria = anyPageQuery();
        ScrollPosition cursor = ScrollPosition.of("cursor-1");
        ScrollResult<OrderEsProjection> expected =
                ScrollResult.of(List.of(projection(1L)), "cursor-2");
        pagedSearcher.nextScrollResult = expected;

        ScrollResult<OrderEsProjection> result =
                query.queryScroll(criteria, cursor, 50, OrderEsProjection.class);

        assertThat(result).isSameAs(expected);
        assertThat(pagedSearcher.capturedCriteria).isSameAs(criteria);
        assertThat(pagedSearcher.capturedCursor).isSameAs(cursor);
        assertThat(pagedSearcher.capturedPageSize).isEqualTo(50);
    }

    // ==================== 检索器未注册兜底 ====================

    @Test
    @DisplayName("按主键检索器未注册时 queryById 抛出 ProjectionSearcherNotFoundException")
    void queryById_withoutSearcher_shouldThrow() {
        OrderQuery emptyRegistryQuery = new OrderQuery(registryWithoutSearchers());

        assertThatThrownBy(() -> emptyRegistryQuery.queryById(1L, OrderEsProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class);
    }

    @Test
    @DisplayName("分页检索器未注册时 queryPage 抛出 ProjectionSearcherNotFoundException")
    void queryPage_withoutSearcher_shouldThrow() {
        OrderQuery emptyRegistryQuery = new OrderQuery(registryWithoutSearchers());

        assertThatThrownBy(() ->
                emptyRegistryQuery.queryPage(anyPageQuery(), PageRequest.of(1, 10), OrderEsProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class);
    }

    /**
     * 只标记索引级全量投影、不登记任何检索器的注册表。
     *
     * <p>必须先标记来源投影，否则门面会先因子投影无来源而抛
     * {@link ProjectionReducerNotFoundException}，无法触达检索器未登记的分支。</p>
     */
    private ProjectorRegistry registryWithoutSearchers() {
        ProjectorRegistry empty = new ProjectorRegistry();
        empty.markSourceProjection(OrderEsProjection.class);
        return empty;
    }

    // ==================== 测试数据与假检索器 ====================

    private OrderEsProjection projection(Long orderId) {
        OrderEsProjection projection = new OrderEsProjection();
        projection.setOrderId(orderId);
        return projection;
    }

    private OrderPageQuery anyPageQuery() {
        return new OrderPageQuery.ByConditions(
                Optional.of(1001L),
                Optional.empty(),
                Optional.empty(),
                Optional.of("机械键盘"),
                Optional.of(2001L));
    }

    /** 记录型假检索器：捕获入参并返回预置结果，用于验证 OrderQuery 的纯转发行为。 */
    private static class RecordingByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

        private Object capturedId;
        private List<Object> capturedIds;
        private Class<OrderEsProjection> capturedType;
        private OrderEsProjection nextResult;
        private List<OrderEsProjection> nextResults = List.of();

        @Override
        public Class<OrderEsProjection> projectionType() {
            return OrderEsProjection.class;
        }

        @Override
        public OrderEsProjection getById(Object id, Class<OrderEsProjection> projectionType) {
            this.capturedId = id;
            this.capturedType = projectionType;
            return nextResult;
        }

        @Override
        public List<OrderEsProjection> getByIds(List<Object> ids, Class<OrderEsProjection> projectionType) {
            this.capturedIds = ids;
            this.capturedType = projectionType;
            return nextResults;
        }
    }

    /** 记录型假列表检索器：服务 OrderListQuery 条件族。 */
    private static class RecordingListSearcher implements IProjectionSearcher<OrderListQuery, OrderEsProjection> {

        private OrderListQuery capturedCriteria;
        private Class<OrderEsProjection> capturedType;
        private List<OrderEsProjection> nextResults = List.of();

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
            this.capturedCriteria = condition;
            this.capturedType = projectionType;
            return nextResults;
        }
    }

    /** 记录型假单条检索器：服务 OrderOneQuery 条件族。 */
    private static class RecordingOneSearcher implements IProjectionSearcher<OrderOneQuery, OrderEsProjection> {

        private OrderOneQuery capturedCriteria;
        private List<OrderEsProjection> nextResults = List.of();

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
            this.capturedCriteria = condition;
            return nextResults;
        }
    }

    /** 记录型假分页检索器：服务 OrderPageQuery 条件族的分页与滚动两条路径。 */
    private static class RecordingPagedSearcher implements IProjectionPagedSearcher<OrderPageQuery, OrderEsProjection> {

        private OrderPageQuery capturedCriteria;
        private PageRequest capturedPageRequest;
        private ScrollPosition capturedCursor;
        private int capturedPageSize;
        private PageResult<OrderEsProjection> nextPageResult;
        private ScrollResult<OrderEsProjection> nextScrollResult;

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
                OrderPageQuery condition,
                PageRequest pageRequest,
                Class<OrderEsProjection> projectionType) {
            this.capturedCriteria = condition;
            this.capturedPageRequest = pageRequest;
            return nextPageResult;
        }

        @Override
        public ScrollResult<OrderEsProjection> searchScroll(
                OrderPageQuery condition,
                ScrollPosition cursor,
                int pageSize,
                Class<OrderEsProjection> projectionType) {
            this.capturedCriteria = condition;
            this.capturedCursor = cursor;
            this.capturedPageSize = pageSize;
            return nextScrollResult;
        }
    }
}
