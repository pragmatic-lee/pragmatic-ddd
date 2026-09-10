package io.pragmatic.ddd.example.order.infrastructure.config.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.reconciliation.IReconciliationCandidateProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单对账候选 ID 提供者：基于 updated_at 时间窗口，返回最近一批（窗口内）发生变更的订单 ID。
 * 具体查询委托 {@link OrderRepository}，扫描器周期调用 {@link #nextBatch(int)} 推进批次，
 * 返回空集合表示本轮扫描结束（窗口内已无更多变更）。
 *
 * @author wizard-lee
 */
@Component
public class OrderReconciliationCandidateProvider
        implements IReconciliationCandidateProvider<Long> {

    private final OrderRepository orderRepository;
    private final int changeWindowMinutes;

    public OrderReconciliationCandidateProvider(OrderRepository orderRepository,
            @Value("${order.reconcile.scan.change-window-minutes:10}") int changeWindowMinutes) {
        this.orderRepository = orderRepository;
        this.changeWindowMinutes = changeWindowMinutes;
    }

    @Override
    public Class<? extends AggregateRoot<Long>> aggregateType() {
        return Order.class;
    }

    @Override
    public List<Long> nextBatch(int batchSize) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(changeWindowMinutes);
        return orderRepository.findReconcileCandidateIds(since, batchSize);
    }
}
