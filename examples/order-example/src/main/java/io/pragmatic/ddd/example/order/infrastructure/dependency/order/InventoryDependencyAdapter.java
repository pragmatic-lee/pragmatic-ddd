package io.pragmatic.ddd.example.order.infrastructure.dependency.order;

import io.pragmatic.ddd.example.order.domain.order.dependency.IInventoryDependency;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存依赖 Stub 适配器：以日志模拟预占与释放，用于演示补偿链路。
 * 可通过 example.inventory.reserve-fail 开关模拟"外部预占失败"。
 *
 * @author wizard-lee
 */
@Component
public class InventoryDependencyAdapter implements IInventoryDependency {

    private final boolean reserveFail;

    public InventoryDependencyAdapter(@Value("${example.inventory.reserve-fail:false}") boolean reserveFail) {
        this.reserveFail = reserveFail;
    }

    @Override
    public String reserve(Long orderId, List<OrderItem> items) {
        if (this.reserveFail) {
            throw new IllegalStateException("库存预占失败: orderId=" + orderId);
        }
        String reserveNo = "RES-" + orderId;
        System.out.println("[inventory] reserve  orderId=" + orderId + " -> " + reserveNo);
        return reserveNo;
    }

    @Override
    public void release(String reserveNo) {
        // 必须幂等：补偿可能被重试、被中继重放
        System.out.println("[inventory] release  reserveNo=" + reserveNo);
    }
}
