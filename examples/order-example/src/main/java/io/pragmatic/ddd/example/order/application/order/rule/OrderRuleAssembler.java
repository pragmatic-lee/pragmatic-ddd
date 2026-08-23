package io.pragmatic.ddd.example.order.application.order.rule;

import io.pragmatic.ddd.example.order.domain.order.rule.OrderRule;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import org.springframework.stereotype.Component;

/**
 * 组装订单规则 OrderRule：封装其对领域服务 IOrderCustomerPermissionService 的构造依赖，
 * 使调用方（如写服务）只接收组装完成的 OrderRule。
 */
@Component
public class OrderRuleAssembler {

    private final IOrderCustomerPermissionService permissionService;

    public OrderRuleAssembler(IOrderCustomerPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public OrderRule assemble() {
        return new OrderRule(permissionService);
    }
}
