package io.pragmatic.ddd.example.order.infrastructure.config.order;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationContribution;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单专属对账接线：仅登记 Order 聚合与其仓储到共享 Registry。
 * 版本解析器 / 补同步器 / 去重 / Manager 均由通用 ReconciliationConfig 提供，
 * 本类不感知 Registry 的构建细节。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderReconciliationConfig {

    @Bean
    public ReconciliationContribution orderReconciliationContribution(OrderRepository orderRepository) {
        return registry -> registry.registerRepository(Order.class, orderRepository);
    }
}
