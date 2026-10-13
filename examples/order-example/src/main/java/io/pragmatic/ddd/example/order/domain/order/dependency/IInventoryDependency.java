package io.pragmatic.ddd.example.order.domain.order.dependency;

import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;

import java.util.List;

/**
 * 库存依赖契约：领域层声明"需要外部库存系统配合完成的一件事"，
 * 具体实现由基础设施层适配（示例为 Stub 适配器）。
 *
 * @author wizard-lee
 */
public interface IInventoryDependency {

    /**
     * 预占库存。
     *
     * @param orderId 订单标识
     * @param items   订单明细
     * @return 预占单号
     */
    String reserve(Long orderId, List<OrderItem> items);

    /**
     * 释放预占，必须幂等。
     *
     * @param reserveNo 预占单号
     */
    void release(String reserveNo);
}
