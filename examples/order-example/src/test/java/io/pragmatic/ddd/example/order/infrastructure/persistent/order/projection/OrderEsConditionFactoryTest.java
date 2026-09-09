package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.jackson.JacksonJsonpGenerator;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单 ES 检索条件构建器的单元测试：验证条件族到 ES Query 的翻译结果。
 *
 * @author wizard-lee
 */
class OrderEsConditionFactoryTest {

    private static OrderPageQuery.ByConditions emptyConditions() {
        return new OrderPageQuery.ByConditions(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    private static String json(Query query) {
        StringWriter writer = new StringWriter();
        try {
            JsonGenerator generator = new JsonFactory().createGenerator(writer);
            query.serialize(new JacksonJsonpGenerator(generator), new JacksonJsonpMapper());
            generator.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString();
    }

    @Nested
    @DisplayName("缺省条件")
    class EmptyConditions {

        @Test
        @DisplayName("全空条件不生成任何 filter 与 must，等价于 match_all")
        void build_emptyConditions_noClause() {
            Query query = OrderEsConditionFactory.build(emptyConditions());

            assertThat(query.isBool()).isTrue();
            assertThat(query.bool().must()).isEmpty();
            assertThat(query.bool().filter()).isEmpty();
        }
    }

    @Nested
    @DisplayName("精确匹配")
    class TermConditions {

        @Test
        @DisplayName("订单号精确匹配订单号字段")
        void build_orderId_term() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.of(1001L), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            assertThat(json(filter.get(0))).contains("\"orderId\"").contains("1001");
        }

        @Test
        @DisplayName("客户 ID 精确匹配嵌套字段")
        void build_customerId_termNestedField() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(2001L), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            assertThat(json(filter.get(0))).contains("customer.customerId").contains("2001");
        }

        @Test
        @DisplayName("订单生命周期状态按 long 值精确匹配")
        void build_status_termAsLong() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.of(1), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            assertThat(json(filter.get(0))).contains("\"status\"").contains("1");
        }

        @Test
        @DisplayName("支付状态精确匹配 paymentStatus 字段")
        void build_paymentStatus_term() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.of(2),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            assertThat(json(filter.get(0))).contains("paymentStatus").contains("2");
        }

        @Test
        @DisplayName("物流状态精确匹配 shipmentStatus 字段")
        void build_shipmentStatus_term() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(4), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            assertThat(json(filter.get(0))).contains("shipmentStatus").contains("4");
        }

        @Test
        @DisplayName("物流单号精确匹配且去除首尾空白")
        void build_trackingNo_termTrimmed() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.of("  SF001  "),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            assertThat(json(filter.get(0))).contains("logisticsInfo.trackingNo").contains("SF001");
        }

        @Test
        @DisplayName("空白物流单号视为未传")
        void build_blankTrackingNo_ignored() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.of("   "),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            assertThat(query.bool().filter()).isEmpty();
        }
    }

    @Nested
    @DisplayName("模糊匹配")
    class MatchConditions {

        @Test
        @DisplayName("备注走 must 中的 match 查询")
        void build_remark_matchInMust() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of("尽快发货"), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            assertThat(query.bool().filter()).isEmpty();
            List<Query> must = query.bool().must();
            assertThat(must).hasSize(1);
            assertThat(json(must.get(0))).contains("\"remark\"").contains("尽快发货");
        }

        @Test
        @DisplayName("空白备注视为未传")
        void build_blankRemark_ignored() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of("  "), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            assertThat(query.bool().must()).isEmpty();
        }
    }

    @Nested
    @DisplayName("区间条件")
    class RangeConditions {

        @Test
        @DisplayName("只传金额下限则只生成 gte，且单位为元不做换算")
        void build_minAmountOnly_onlyGte() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(new BigDecimal("100.00")), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            List<Query> filter = query.bool().filter();
            assertThat(filter).hasSize(1);
            String json = json(filter.get(0));
            assertThat(json).contains("totalAmount").contains("gte").contains("100.00");
            assertThat(json).doesNotContain("lte");
        }

        @Test
        @DisplayName("金额上下限同时传入时生成闭区间")
        void build_amountRange_closedRange() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.of(new BigDecimal("100.00")),
                    Optional.of(new BigDecimal("500.00")), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty()));

            String json = json(query.bool().filter().get(0));
            assertThat(json).contains("gte").contains("100.00").contains("lte").contains("500.00");
        }

        @Test
        @DisplayName("支付时间起点展开为当日零时")
        void build_paidFrom_startOfDay() {
            LocalDateTime from = LocalDate.of(2026, 8, 1).atStartOfDay();

            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(from), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));

            String json = json(query.bool().filter().get(0));
            assertThat(json).contains("paidAt").contains("2026-08-01T00:00");
            assertThat(json).doesNotContain("lte");
        }

        @Test
        @DisplayName("创建时间终点展开为当日 23:59:59.999")
        void build_createdTo_endOfDay() {
            LocalDateTime to = LocalDate.of(2026, 8, 1).atTime(LocalTime.of(23, 59, 59, 999_000_000));

            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.of(to), Optional.empty()));

            String json = json(query.bool().filter().get(0));
            assertThat(json).contains("createdAt").contains("2026-08-01T23:59:59.999");
            assertThat(json).doesNotContain("gte");
        }
    }

    @Nested
    @DisplayName("条件叠加")
    class CombinedConditions {

        @Test
        @DisplayName("六个精确条件 + 金额区间进 filter，备注与商品名进 must")
        void build_mixedConditions_splitFilterAndMust() {
            Query query = OrderEsConditionFactory.build(new OrderPageQuery.ByConditions(
                    Optional.of(1001L), Optional.of(1), Optional.of(2),
                    Optional.of(4), Optional.of(2001L), Optional.of("SF001"),
                    Optional.of("尽快"), Optional.of(new BigDecimal("100.00")),
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.of("键盘")));

            assertThat(query.bool().filter()).hasSize(7);
            assertThat(query.bool().must()).hasSize(2);
        }
    }
}
