package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reader;

import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderReadSource;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderRowProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.exception.ProjectionExceptions;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单同源直读源实现：经 SqlSessionTemplate 执行 OrderRowMapper（纯 XML），产出订单行投影。
 *
 * <p>与 ES / Redis 源的区别：不继承 {@code AbstractProjectionSource}，因此没有 materialize /
 * purge / readVersion / rebuild；不定义 REPLICA_ID、不发数据同步事件、不参与对账。
 * 读侧只做两件事——查行投影、按目标类型取裁剪器。</p>
 *
 * <p>分页走 offset + count；滚动走 keyset（排序键 created_at + order_id，唯一且不可变），
 * 不使用 offset 模拟滚动，避免深翻页逐行扫描。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderDbReadSource implements IOrderReadSource {

    private static final String STATEMENT_SELECT_ROW_BY_ID = "OrderRowMapper.selectRowById";

    private static final String STATEMENT_SELECT_ROWS_BY_IDS = "OrderRowMapper.selectRowsByIds";

    private static final String STATEMENT_COUNT_BY_CONDITION = "OrderRowMapper.countByCondition";

    private static final String STATEMENT_SELECT_PAGE_BY_CONDITION = "OrderRowMapper.selectPageByCondition";

    private static final String STATEMENT_SELECT_SCROLL_BY_CONDITION = "OrderRowMapper.selectScrollByCondition";

    private static final String CURSOR_SEPARATOR = "|";

    private final SqlSessionTemplate sqlSessionTemplate;

    private final List<IReducer<OrderRowProjection, ?>> reducers;

    public OrderDbReadSource(SqlSessionTemplate sqlSessionTemplate,
                             List<IReducer<OrderRowProjection, ?>> reducers) {
        this.sqlSessionTemplate = sqlSessionTemplate;
        this.reducers = List.copyOf(reducers);
    }

    @Override
    public OrderRowProjection getById(Object id) {
        Long orderId = Long.valueOf(id.toString());
        return ProjectionExceptions.retrieve(() -> {
            OrderRowProjection row = this.sqlSessionTemplate.selectOne(
                    STATEMENT_SELECT_ROW_BY_ID, Map.of("orderId", orderId));
            this.fillStatusName(row);
            return row;
        }, "getById");
    }

    @Override
    public List<OrderRowProjection> getByIds(List<Object> ids) {
        List<Long> orderIds = ids.stream().map(id -> Long.valueOf(id.toString())).toList();
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return ProjectionExceptions.retrieve(() -> {
            List<OrderRowProjection> rows = this.sqlSessionTemplate.selectList(
                    STATEMENT_SELECT_ROWS_BY_IDS, Map.of("orderIds", orderIds));
            rows.forEach(this::fillStatusName);
            return rows;
        }, "getByIds");
    }

    @Override
    public PageResult<OrderRowProjection> searchPage(OrderPageQuery condition, PageRequest pageRequest) {
        return ProjectionExceptions.retrieve(() -> {
            Map<String, Object> params = this.conditionParams(condition);
            Long total = this.sqlSessionTemplate.selectOne(STATEMENT_COUNT_BY_CONDITION, params);
            params.put("offset", pageRequest.offset());
            params.put("pageSize", pageRequest.pageSize());
            List<OrderRowProjection> rows = this.sqlSessionTemplate.selectList(
                    STATEMENT_SELECT_PAGE_BY_CONDITION, params);
            rows.forEach(this::fillStatusName);
            return PageResult.of(rows, Optional.ofNullable(total).orElse(0L), pageRequest);
        }, "searchPage");
    }

    @Override
    public ScrollResult<OrderRowProjection> searchScroll(
            OrderPageQuery condition, ScrollPosition cursor, int pageSize) {
        return ProjectionExceptions.retrieve(() -> {
            Map<String, Object> params = this.conditionParams(condition);
            this.putCursor(params, cursor);
            params.put("pageSize", pageSize);
            List<OrderRowProjection> rows = this.sqlSessionTemplate.selectList(
                    STATEMENT_SELECT_SCROLL_BY_CONDITION, params);
            rows.forEach(this::fillStatusName);
            return ScrollResult.of(rows, this.nextCursor(rows));
        }, "searchScroll");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <X extends IOrderProjection> IReducer<OrderRowProjection, X> getReducer(Class<X> target) {
        return this.reducers.stream()
                .filter(reducer -> target.isAssignableFrom(reducer.projectionType()))
                .map(reducer -> (IReducer<OrderRowProjection, X>) reducer)
                .findFirst()
                .orElse(null);
    }

    /** 状态 code 来自 SQL，名称由读源回填，避免 SQL 写死展示文案。 */
    private void fillStatusName(OrderRowProjection row) {
        if (row == null) {
            return;
        }
        Optional.ofNullable(OrderStatus.of(row.getStatus()))
                .ifPresent(status -> row.setStatusName(status.getName()));
    }

    private Map<String, Object> conditionParams(OrderPageQuery condition) {
        Map<String, Object> params = new HashMap<>();
        if (condition instanceof OrderPageQuery.ByConditions byConditions) {
            params.put("orderId", byConditions.orderId().orElse(null));
            params.put("status", byConditions.status().orElse(null));
            params.put("createdFrom", byConditions.createdFrom().orElse(null));
            params.put("createdTo", byConditions.createdTo().orElse(null));
        }
        return params;
    }

    /** 游标对调用方不透明：此处编码为「创建时间|订单号」，由本类成对编解码。 */
    private void putCursor(Map<String, Object> params, ScrollPosition cursor) {
        if (cursor.isInitial()) {
            return;
        }
        String[] parts = cursor.cursor().split("\\" + CURSOR_SEPARATOR, 2);
        params.put("lastCreatedAt", LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        params.put("lastOrderId", Long.valueOf(parts[1]));
    }

    private String nextCursor(List<OrderRowProjection> rows) {
        if (rows.isEmpty()) {
            return null;
        }
        OrderRowProjection last = rows.get(rows.size() - 1);
        if (last.getCreatedAt() == null || last.getOrderId() == null) {
            return null;
        }
        return last.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + CURSOR_SEPARATOR
                + last.getOrderId();
    }
}
