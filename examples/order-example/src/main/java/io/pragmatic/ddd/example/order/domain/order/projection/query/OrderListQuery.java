package io.pragmatic.ddd.example.order.domain.order.projection.query;

import io.pragmatic.ddd.repository.query.ListQueryCriteria;

/**
 * 订单列表查询（queryList）的条件族。
 *
 * <p>继承框架分族父类 {@link ListQueryCriteria}，族内以 sealed interface + record permits
 * 横向扩展具体场景；新增场景只需新增 record 并在 permits 登记，穷举分支由编译器担保。</p>
 *
 * <p>本族收敛为两个 TOP N 列表查询场景（非分页）：金额 TOP N 与最近 TOP N，均按客户 + 订单状态精确匹配。</p>
 *
 * @author wizard-lee
 */
public sealed interface OrderListQuery extends ListQueryCriteria
        permits OrderListQuery.TopByAmount,
                OrderListQuery.TopRecent {

    /**
     * 查询指定用户金额 TOP N 的订单。
     *
     * @param top        取前 N 条（如 10、100）
     * @param status     订单状态（精确匹配，基础类型 Integer）
     * @param customerId 客户 ID（精确匹配）
     */
    record TopByAmount(int top, Integer status, Long customerId) implements OrderListQuery {
    }

    /**
     * 查询指定用户最近 TOP N 的订单。
     *
     * @param customerId 客户 ID（精确匹配）
     * @param status     订单状态（精确匹配，非支付状态，基础类型 Integer）
     * @param top        取前 N 条（如 10）
     */
    record TopRecent(Long customerId, Integer status, int top) implements OrderListQuery {
    }
}
