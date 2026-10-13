package io.pragmatic.ddd.example.order.application.order.action;

import java.util.List;

/**
 * 库存预占的持久化载荷：正向执行之前即可确定的补偿依据，供崩溃后由中继还原释放。
 * 不依赖外部返回值——外部系统须支持按业务单号幂等撤销。
 *
 * @param orderId 订单标识
 * @param lines   订单明细摘要
 * @author wizard-lee
 */
public record ReservePayload(Long orderId, List<Line> lines) {

    /**
     * 订单明细摘要。
     *
     * @param productId   商品标识
     * @param productName 商品名
     * @param quantity    数量
     */
    public record Line(Long productId, String productName, Integer quantity) {
    }
}
