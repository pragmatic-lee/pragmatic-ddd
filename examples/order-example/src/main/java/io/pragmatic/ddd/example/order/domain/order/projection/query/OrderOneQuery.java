package io.pragmatic.ddd.example.order.domain.order.projection.query;

import io.pragmatic.ddd.repository.query.OneQueryCriteria;

/**
 * 订单单投影查询（queryOne）的条件族。
 *
 * <p>继承框架分族父类 {@link OneQueryCriteria}，族内以 sealed interface + record permits
 * 横向扩展具体场景；新增场景只需新增 record 并在 permits 登记，穷举分支由编译器担保。</p>
 *
 * @author wizard-lee
 */
public sealed interface OrderOneQuery extends OneQueryCriteria
        permits OrderOneQuery.LatestByCustomer {
    /**
     * 按客户取最新创建的订单。
     *
     * @param customerId 客户 ID（必填、精确匹配）
     */
    record LatestByCustomer(Long customerId) implements OrderOneQuery {
    }
}
