package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ProjectionSource;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OrderReadService 按查询方式转发到领域查询契约，并透传调用方指定的投影类型。
 *
 * <p>用记录型桩件替代真实 IOrderQuery，断言每个方法走的是哪个查询能力、
 * 参数与投影类型是否原样透传、返回值是否原样回传。</p>
 *
 * @author wizard-lee
 */
class OrderReadServiceTest {

    private RecordingOrderQuery recordingQuery;

    private OrderReadService readService;

    @BeforeEach
    void setUp() {
        recordingQuery = new RecordingOrderQuery();
        readService = new OrderReadService(recordingQuery);
    }

    private OrderPageQuery.ByConditions anyPageCondition() {
        return new OrderPageQuery.ByConditions(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    @Test
    @DisplayName("按主键查询转发到 queryById 并透传投影类型")
    void queryById_forwardsToDomainQuery() {
        OrderSummaryProjection result = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryById");
        assertThat(recordingQuery.lastId).isEqualTo(1001L);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(result).isSameAs(recordingQuery.summary);
    }

    @Test
    @DisplayName("同一方法可返回不同投影，由调用方指定")
    void queryById_supportsAnyProjectionType() {
        OrderEsProjection detail = readService.queryById(1001L, OrderEsProjection.class);

        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderEsProjection.class);
        assertThat(detail).isSameAs(recordingQuery.detail);

        OrderSummaryProjection summary = readService.queryById(1001L, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(summary).isSameAs(recordingQuery.summary);
    }

    @Test
    @DisplayName("指定来源投影的主键查询转发到三参 queryById 并分别透传来源与目标投影类型")
    void queryByIdWithSource_forwardsToDomainQuery() {
        OrderSummaryProjection result =
                readService.queryById(1001L, OrderEsProjection.class, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryByIdWithSource");
        assertThat(recordingQuery.lastId).isEqualTo(1001L);
        assertThat(recordingQuery.lastSourceProjection).isEqualTo(OrderEsProjection.class);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(result).isSameAs(recordingQuery.summary);
    }

    @Test
    @DisplayName("同一目标投影可指定不同来源投影，来源按调用方指定透传")
    void queryByIdWithSource_supportsSwitchingSource() {
        OrderSummaryProjection fromEs =
                readService.queryById(1001L, OrderEsProjection.class, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastSourceProjection).isEqualTo(OrderEsProjection.class);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(fromEs).isSameAs(recordingQuery.summary);

        OrderSummaryProjection fromCache =
                readService.queryById(1001L, OrderCacheProjection.class, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastSourceProjection).isEqualTo(OrderCacheProjection.class);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(fromCache).isSameAs(recordingQuery.summary);
    }

    @Test
    @DisplayName("批量主键查询转发到 queryByIds 并透传投影类型")
    void queryByIds_forwardsToDomainQuery() {
        List<Long> ids = List.of(1001L, 1002L);

        List<OrderSummaryProjection> results = readService.queryByIds(ids, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryByIds");
        assertThat(recordingQuery.lastIds).isSameAs(ids);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(results).containsExactly(recordingQuery.summary);
    }

    @Test
    @DisplayName("单条条件查询转发到 queryOne 并透传条件与投影类型")
    void queryOne_forwardsToDomainQuery() {
        OrderOneQuery.LatestByCustomer condition = new OrderOneQuery.LatestByCustomer(2001L);

        OrderSummaryProjection result = readService.queryOne(condition, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryOne");
        assertThat(recordingQuery.lastCondition).isSameAs(condition);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(result).isSameAs(recordingQuery.summary);
    }

    @Test
    @DisplayName("列表条件查询转发到 queryList 并透传条件与投影类型")
    void queryList_forwardsToDomainQuery() {
        OrderListQuery.TopRecent condition = new OrderListQuery.TopRecent(2001L, 2, 5);

        List<OrderEsProjection> results = readService.queryList(condition, OrderEsProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryList");
        assertThat(recordingQuery.lastCondition).isSameAs(condition);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderEsProjection.class);
        assertThat(results).containsExactly(recordingQuery.detail);
    }

    @Test
    @DisplayName("分页查询转发到 queryPage 并透传分页请求与投影类型")
    void queryPage_forwardsToDomainQuery() {
        PageRequest pageRequest = PageRequest.of(2, 20);

        PageResult<OrderSummaryProjection> page =
                readService.queryPage(anyPageCondition(), pageRequest, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryPage");
        assertThat(recordingQuery.lastPageRequest).isSameAs(pageRequest);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(page.data()).containsExactly(recordingQuery.summary);
        assertThat(page.totalCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("滚动查询转发到 queryScroll 并透传游标、批大小与投影类型")
    void queryScroll_forwardsToDomainQuery() {
        ScrollPosition cursor = ScrollPosition.initial();

        ScrollResult<OrderSummaryProjection> scroll =
                readService.queryScroll(anyPageCondition(), cursor, 100, OrderSummaryProjection.class);

        assertThat(recordingQuery.lastMethod).isEqualTo("queryScroll");
        assertThat(recordingQuery.lastCursor).isSameAs(cursor);
        assertThat(recordingQuery.lastPageSize).isEqualTo(100);
        assertThat(recordingQuery.lastProjectionType).isEqualTo(OrderSummaryProjection.class);
        assertThat(scroll.data()).containsExactly(recordingQuery.summary);
    }

    /**
     * 记录型查询桩件：记录被调用的方法、入参与投影类型，并按投影类型返回预设实例。
     */
    private static final class RecordingOrderQuery implements IOrderQuery {

        private final OrderEsProjection detail = new OrderEsProjection();

        private final OrderSummaryProjection summary = new OrderSummaryProjection();

        private String lastMethod;

        private Object lastId;

        private Class<?> lastSourceProjection;

        private List<Long> lastIds;

        private Object lastCondition;

        private PageRequest lastPageRequest;

        private ScrollPosition lastCursor;

        private int lastPageSize;

        private Class<?> lastProjectionType;

        @Override
        public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
            lastMethod = "queryById";
            lastId = id;
            lastProjectionType = projectionType;
            return pick(projectionType);
        }

        @Override
        public <X extends IOrderProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
            lastMethod = "queryByIds";
            lastIds = ids;
            lastProjectionType = projectionType;
            return List.of(pick(projectionType));
        }

        @Override
        public <X extends IOrderProjection> X queryOne(OrderOneQuery query, Class<X> projectionType) {
            lastMethod = "queryOne";
            lastCondition = query;
            lastProjectionType = projectionType;
            return pick(projectionType);
        }

        @Override
        public <X extends IOrderProjection> List<X> queryList(OrderListQuery query, Class<X> projectionType) {
            lastMethod = "queryList";
            lastCondition = query;
            lastProjectionType = projectionType;
            return List.of(pick(projectionType));
        }

        @Override
        public <X extends IOrderProjection> PageResult<X> queryPage(
                OrderPageQuery query, PageRequest pageRequest, Class<X> projectionType) {
            lastMethod = "queryPage";
            lastCondition = query;
            lastPageRequest = pageRequest;
            lastProjectionType = projectionType;
            List<X> data = List.of(pick(projectionType));
            return PageResult.of(data, 1L, pageRequest);
        }

        @Override
        public <X extends IOrderProjection> ScrollResult<X> queryScroll(
                OrderPageQuery query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
            lastMethod = "queryScroll";
            lastCondition = query;
            lastCursor = cursor;
            lastPageSize = pageSize;
            lastProjectionType = projectionType;
            List<X> data = List.of(pick(projectionType));
            return ScrollResult.of(data, null);
        }

        /** 按目标投影类型返回对应预设实例，用于验证投影类型的透传关系。 */
        @SuppressWarnings("unchecked")
        private <X> X pick(Class<X> projectionType) {
            if (projectionType == OrderEsProjection.class) {
                return (X) detail;
            }
            return (X) summary;
        }

        @Override
        public <X extends IOrderProjection> X queryById(Object id, Class<?> sourceProjection, Class<X> projectionType) {
            lastMethod = "queryByIdWithSource";
            lastId = id;
            lastSourceProjection = sourceProjection;
            lastProjectionType = projectionType;
            return pick(projectionType);
        }

        @Override
        public io.pragmatic.ddd.repository.query.ProjectionSource source() {
            return null;
        }

        @Override
        public io.pragmatic.ddd.repository.query.IProjectionSourceQuery<Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery> source(
                io.pragmatic.ddd.repository.query.ProjectionSource source) {
            return this;
        }

        @Override
        public io.pragmatic.ddd.repository.query.IProjectionSourceQuery<Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery> fallbackChain(
                List<io.pragmatic.ddd.repository.query.ProjectionSource> sources) {
            return this;
        }
    }
}
