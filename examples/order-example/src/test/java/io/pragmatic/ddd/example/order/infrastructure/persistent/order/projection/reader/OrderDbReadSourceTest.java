package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reader;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderRowProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderRowSummaryReducer;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * OrderDbReadSource 单元测试：不连库，桩掉 SqlSessionTemplate，验证直读源的四个职责——
 * 查行投影、回填状态名、总数取裁剪前、滚动游标由末行编码。
 *
 * @author wizard-lee
 */
@DisplayName("OrderDbReadSource 单元测试")
class OrderDbReadSourceTest {

    private SqlSessionTemplate sqlSessionTemplate;

    private OrderDbReadSource readSource;

    @BeforeEach
    void setUp() {
        this.sqlSessionTemplate = mock(SqlSessionTemplate.class);
        this.readSource = new OrderDbReadSource(this.sqlSessionTemplate, List.of(new OrderRowSummaryReducer()));
    }

    @Test
    @DisplayName("getById 回填状态名，并可经裁剪器转为概要投影")
    void getById_fillsStatusNameAndReduces() {
        doReturn(row(1001L, 2, LocalDateTime.of(2026, 10, 1, 10, 0)))
                .when(this.sqlSessionTemplate)
                .selectOne(eq("OrderRowMapper.selectRowById"), any());

        OrderRowProjection row = this.readSource.getById(1001L);

        assertThat(row.getStatusName()).isEqualTo("已完成");
        IReducer<OrderRowProjection, OrderSummaryProjection> reducer =
                this.readSource.getReducer(OrderSummaryProjection.class);
        assertThat(reducer).isNotNull();
        assertThat(reducer.reduce(row).getOrderId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("searchPage 总数来自 count，数据行按页返回")
    void searchPage_keepsTotalFromCount() {
        doReturn(3L)
                .when(this.sqlSessionTemplate)
                .selectOne(eq("OrderRowMapper.countByCondition"), any());
        doReturn(List.of(row(1002L, 1, LocalDateTime.of(2026, 10, 1, 10, 0))))
                .when(this.sqlSessionTemplate)
                .selectList(eq("OrderRowMapper.selectPageByCondition"), any());

        PageResult<OrderRowProjection> page = this.readSource.searchPage(condition(), PageRequest.of(1, 20));

        assertThat(page.totalCount()).isEqualTo(3L);
        assertThat(page.data()).hasSize(1);
        assertThat(page.data().get(0).getStatusName()).isEqualTo("进行中");
    }

    @Test
    @DisplayName("searchScroll 用末行排序键编码下一游标")
    void searchScroll_encodesCursorFromLastRow() {
        doReturn(List.of(
                row(1002L, 1, LocalDateTime.of(2026, 10, 1, 10, 0)),
                row(1001L, 1, LocalDateTime.of(2026, 10, 1, 9, 0))))
                .when(this.sqlSessionTemplate)
                .selectList(eq("OrderRowMapper.selectScrollByCondition"), any());

        ScrollResult<OrderRowProjection> result =
                this.readSource.searchScroll(condition(), ScrollPosition.initial(), 2);

        assertThat(result.data()).hasSize(2);
        assertThat(result.nextCursor()).isEqualTo("2026-10-01T09:00:00|1001");
    }

    private OrderRowProjection row(Long orderId, int status, LocalDateTime createdAt) {
        OrderRowProjection row = new OrderRowProjection();
        row.setOrderId(orderId);
        row.setStatus(status);
        row.setActualAmount(new BigDecimal("88.00"));
        row.setCreatedAt(createdAt);
        return row;
    }

    private OrderPageQuery condition() {
        return new OrderPageQuery.ByConditions(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
