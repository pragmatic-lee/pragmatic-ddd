package io.pragmatic.ddd.example.order.domain.order.projection.query;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 订单分页 / 滚动查询（queryPage / queryScroll）共用的条件族。
 *
 * <p>继承框架分族父类 {@link PageQueryCriteria}，族内以 sealed interface + record permits
 * 横向扩展具体场景；本族字段全 Optional（按需过滤），与 One / List 族的精确必填语义区分。</p>
 *
 * @author wizard-lee
 */
public sealed interface OrderPageQuery extends PageQueryCriteria
        permits OrderPageQuery.ByConditions {
    /**
     * 订单列表综合检索条件，各字段均 Optional（不传则不参与筛选）。
     * 金额单位统一为「元」（与 Money.amount、投影、ES 存储一致），时间统一为 LocalDateTime。
     *
     * @param orderId      订单号（精确匹配）
     * @param status       订单状态（精确匹配，基础类型 Integer）
     * @param customerId   客户 ID（精确匹配）
     * @param trackingNo   物流单号（精确匹配）
     * @param remark       订单备注（分词匹配）
     * @param minAmount    总金额下限（元，含）
     * @param maxAmount    总金额上限（元，含）
     * @param paidFrom     支付时间起（含）
     * @param paidTo       支付时间止（含）
     * @param createdFrom  创建时间起（含）
     * @param createdTo    创建时间止（含）
     * @param productName  商品名称（分词匹配）
     */
    record ByConditions(
            Optional<Long> orderId,
            Optional<Integer> status,
            Optional<Long> customerId,
            Optional<String> trackingNo,
            Optional<String> remark,
            Optional<BigDecimal> minAmount,
            Optional<BigDecimal> maxAmount,
            Optional<LocalDateTime> paidFrom,
            Optional<LocalDateTime> paidTo,
            Optional<LocalDateTime> createdFrom,
            Optional<LocalDateTime> createdTo,
            Optional<String> productName) implements OrderPageQuery {
    }
}
