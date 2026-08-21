package io.pragmatic.ddd.example.order.domain.order.rule;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.service.IOrderCustomerPermissionService;
import io.pragmatic.ddd.rules.ActiveStatus;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.IActiveRuleCondition;
import io.pragmatic.ddd.rules.RuleCheckResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单聚合业务规则容器，承载订单全生命周期的不变性约束。
 * <p>
 * 作为聚合维度的规则容器，在构造函数中一次性注册订单的全部不变量；
 * 依赖外部系统的下单用户资格校验通过领域服务契约接入，由调用方注入实现。
 *
 * @author wizard-lee
 */
public class OrderRule extends EntityRule<Order> {

    private final IOrderCustomerPermissionService customerPermissionService;

    public OrderRule(IOrderCustomerPermissionService customerPermissionService) {
        super();
        this.customerPermissionService = customerPermissionService;
        this.registerRules();
    }

    private void registerRules() {
        // 订单金额必须为正数：订单总额不允许为零或负值，否则视为非法订单。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        order.getTotalAmount() != null
                                && order.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO) > 0)),
                OrderRuleRegistry.ORDER_AMOUNT_POSITIVE);
        // 订单至少包含一个订单项：空订单无业务意义，禁止创建不含明细的订单。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        !order.getOrderItems().getAllItems().isEmpty())),
                OrderRuleRegistry.ORDER_AT_LEAST_ONE_ITEM);
        // 订单项数量必须为正数：每个订单项的购买数量必须大于零。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        this.allItemQuantityPositive(order.getOrderItems().getAllItems()))),
                OrderRuleRegistry.ORDER_ITEM_QUANTITY_POSITIVE);
        // 订单项单价必须为正数：每个订单项的单价必须大于零。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        this.allItemPricePositive(order.getOrderItems().getAllItems()))),
                OrderRuleRegistry.ORDER_ITEM_PRICE_POSITIVE);
        // 仅允许取消待支付或已支付状态的订单：其他状态的订单不可发起取消。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(this.cancelStatusValid(order))),
                OrderRuleRegistry.ORDER_CANCEL_STATUS_INVALID);
        // 仅允许待支付状态的订单修改收货地址：订单进入后续流程后收货地址不可变更。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(this.addressChangeStatusValid(order))),
                OrderRuleRegistry.ORDER_ADDRESS_CHANGE_STATUS_INVALID);
        // 订单客户信息必填：下单必须关联有效的客户，客户信息不允许为空。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(order.getCustomer() != null)),
                OrderRuleRegistry.ORDER_CUSTOMER_REQUIRED);
        // 收货地址必填：订单必须包含收货地址，不允许缺失配送信息。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(order.getShippingAddress() != null)),
                OrderRuleRegistry.ORDER_ADDRESS_REQUIRED);
        // 下单用户资格校验：仅在下单创建（CREATED）时激活，校验外部用户是否处于生效状态且具备下单资格。
        this.addRule(
                EntityRule.of(order -> this.verifyCustomer(order.getCustomer())),
                OrderRuleRegistry.ORDER_CUSTOMER_QUALIFIED,
                IActiveRuleCondition.of(order -> order.getStatus() == OrderStatus.CREATED
                        ? ActiveStatus.ACTIVE
                        : ActiveStatus.INACTIVE));
    }

    private boolean allItemQuantityPositive(List<OrderItem> items) {
        return items.stream()
                .allMatch(item -> item.getQuantity() > 0);
    }

    private boolean allItemPricePositive(List<OrderItem> items) {
        return items.stream()
                .allMatch(item -> item.getPrice() != null
                        && item.getPrice().getAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean cancelStatusValid(Order order) {
        OrderStatus status = order.getStatus();
        if (status == null) {
            return true;
        }
        return status == OrderStatus.CREATED || status == OrderStatus.PAID;
    }

    private boolean addressChangeStatusValid(Order order) {
        OrderStatus status = order.getStatus();
        if (status == null) {
            return true;
        }
        return status == OrderStatus.CREATED;
    }

    private RuleCheckResult verifyCustomer(Customer customer) {
        return this.customerPermissionService.verifyOrderCreatePermission(customer);
    }
}
