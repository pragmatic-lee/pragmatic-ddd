package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderReconcileHandle;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationManager;
import org.springframework.stereotype.Component;

/**
 * 订单数据同步事件的写后延迟复核领域服务：复用与数据异构投影同一事件，
 * 仅以 DELAYED 策略声明，触发对订单全部读副本的对账自愈。
 *
 * @author wizard-lee
 */
@Component
public class OrderReconcileHandle implements IOrderReconcileHandle {

    private final ReconciliationManager reconciliationManager;

    public OrderReconcileHandle(ReconciliationManager reconciliationManager) {
        this.reconciliationManager = reconciliationManager;
    }

    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        reconciliationManager.reconcile(Order.class, id);
    }
}
