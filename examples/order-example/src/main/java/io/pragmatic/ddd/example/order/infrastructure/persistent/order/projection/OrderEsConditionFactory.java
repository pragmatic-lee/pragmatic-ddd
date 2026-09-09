package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 订单 ES 检索条件的纯函数构建器：把条件族的 Optional 字段翻译成 ES Query。
 * 不持有 ElasticsearchClient，便于单元测试直接断言 Query 结构。
 *
 * <p>精确 / 范围条件进 filter（不参与评分、可被缓存），文本模糊条件进 must。</p>
 *
 * @author wizard-lee
 */
final class OrderEsConditionFactory {

    private OrderEsConditionFactory() {
    }

    /**
     * 构建订单综合检索条件对应的 bool 查询。
     *
     * @param condition 综合检索条件族
     * @return ES bool 查询；条件全缺省时等价于 match_all
     */
    static Query build(OrderPageQuery.ByConditions condition) {
        List<Query> filter = Stream.of(
                        termLong("orderId", condition.orderId()),
                        termInt("status", condition.status()),
                        termInt("paymentStatus", condition.paymentStatus()),
                        termInt("shipmentStatus", condition.shipmentStatus()),
                        termLong("customer.customerId", condition.customerId()),
                        termKeyword("logisticsInfo.trackingNo", condition.trackingNo()),
                        decimalRange("totalAmount", condition.minAmount(), condition.maxAmount()),
                        dateRange("paidAt", condition.paidFrom(), condition.paidTo()),
                        dateRange("createdAt", condition.createdFrom(), condition.createdTo()))
                .flatMap(Optional::stream)
                .toList();
        List<Query> must = Stream.of(
                        match("remark", condition.remark()),
                        match("itemProductNamesText", condition.productName()))
                .flatMap(Optional::stream)
                .toList();
        return Query.of(q -> q.bool(BoolQuery.of(b -> b.must(must).filter(filter))));
    }

    /**
     * 精确匹配：long 字段。
     *
     * @param field 字段名
     * @param value 待匹配值，empty 表示不参与筛选
     * @return term 查询，value 为 empty 时返回 empty
     */
    static Optional<Query> termLong(String field, Optional<Long> value) {
        return value.map(v -> Query.of(q -> q.term(TermQuery.of(t ->
                t.field(field).value(FieldValue.of(v))))));
    }

    /**
     * 精确匹配：integer 字段，统一按 long 值写入避免类型歧义。
     *
     * @param field 字段名
     * @param value 待匹配值，empty 表示不参与筛选
     * @return term 查询，value 为 empty 时返回 empty
     */
    static Optional<Query> termInt(String field, Optional<Integer> value) {
        return value.map(v -> Query.of(q -> q.term(TermQuery.of(t ->
                t.field(field).value(FieldValue.of(v.longValue()))))));
    }

    /**
     * 精确匹配：keyword 字段（单号等），空白串视为未传。
     *
     * @param field 字段名
     * @param value 待匹配值，empty 或空白串表示不参与筛选
     * @return term 查询，value 为 empty / 空白时返回 empty
     */
    static Optional<Query> termKeyword(String field, Optional<String> value) {
        return value
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> Query.of(q -> q.term(TermQuery.of(t ->
                        t.field(field).value(FieldValue.of(v))))));
    }

    /**
     * 分词匹配：text 字段，空白串视为未传。
     *
     * @param field 字段名
     * @param value 待匹配值，empty 或空白串表示不参与筛选
     * @return match 查询，value 为 empty / 空白时返回 empty
     */
    static Optional<Query> match(String field, Optional<String> value) {
        return value
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> Query.of(q -> q.match(MatchQuery.of(m -> m.field(field).query(v)))));
    }

    /**
     * 金额区间（元），单侧缺省则只加一侧边界，闭区间。
     *
     * @param field 字段名
     * @param min   下限（含），empty 表示不限
     * @param max   上限（含），empty 表示不限
     * @return range 查询，两侧均 empty 时返回 empty
     */
    static Optional<Query> decimalRange(String field, Optional<BigDecimal> min, Optional<BigDecimal> max) {
        if (min.isEmpty() && max.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Query.of(q -> q.range(RangeQuery.of(r -> r.untyped(u -> {
            u.field(field);
            min.ifPresent(v -> u.gte(JsonData.of(v)));
            max.ifPresent(v -> u.lte(JsonData.of(v)));
            return u;
        })))));
    }

    /**
     * 时间区间，闭区间；时间以 ISO_LOCAL_DATE_TIME 字符串传入，
     * 规避 ES 客户端把 LocalDateTime 序列化为数组导致 date 字段解析失败。
     *
     * @param field 字段名
     * @param from  起始时间（含），empty 表示不限
     * @param to    截止时间（含），empty 表示不限
     * @return range 查询，两侧均 empty 时返回 empty
     */
    static Optional<Query> dateRange(String field, Optional<LocalDateTime> from, Optional<LocalDateTime> to) {
        if (from.isEmpty() && to.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Query.of(q -> q.range(RangeQuery.of(r -> r.date(d -> {
            d.field(field);
            from.ifPresent(v -> d.gte(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(v)));
            to.ifPresent(v -> d.lte(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(v)));
            return d;
        })))));
    }
}
