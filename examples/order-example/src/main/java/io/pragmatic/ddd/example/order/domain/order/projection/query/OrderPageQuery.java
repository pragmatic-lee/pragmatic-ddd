package io.pragmatic.ddd.example.order.domain.order.projection.query;

import io.pragmatic.ddd.repository.query.PageQueryCriteria;

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
     * 分页 / 滚动查询的复合条件，各字段均 Optional（不传则不参与筛选）。
     * 枚举状态按出入参规约统一使用基础类型 Integer。
     *
     * @param orderId     订单号（精确匹配）
     * @param payStatus   支付状态（精确匹配）
     * @param totalAmount 订单总金额（精确匹配）
     * @param productName 商品名称（模糊匹配）
     * @param customerId  客户 ID（精确匹配）
     */
    record ByConditions(
            Optional<Long> orderId,
            Optional<Integer> payStatus,
            Optional<Long> totalAmount,
            Optional<String> productName,
            Optional<Long> customerId) implements OrderPageQuery {
    }
}
