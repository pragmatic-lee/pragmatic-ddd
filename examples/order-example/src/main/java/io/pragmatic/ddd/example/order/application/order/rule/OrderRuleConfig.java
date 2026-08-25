package io.pragmatic.ddd.example.order.application.order.rule;

import io.pragmatic.ddd.example.order.domain.order.repository.IOrderRepository;
import io.pragmatic.ddd.example.order.domain.order.rule.OrderRule;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单规则装配配置：将 OrderRule 注册为 Spring 单例 Bean，
 * 显式声明其对领域服务 IOrderCustomerPermissionService 与仓储 IOrderRepository 的构造依赖，
 * 使写服务等调用方直接注入 OrderRule，领域层保持零 Spring 依赖。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderRuleConfig {

    @Bean
    public OrderRule orderRule(IOrderCustomerPermissionService permissionService,
                               IOrderRepository orderRepository) {
        return new OrderRule(permissionService, orderRepository);
    }
}
