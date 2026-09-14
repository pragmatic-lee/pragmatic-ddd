package io.pragmatic.ddd.example.order.domain.order.rule;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.ShipmentStatus;
import io.pragmatic.ddd.example.order.domain.order.operation.OrderOperationRegistry;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.repository.IOrderRepository;
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

    private final IOrderRepository orderRepository;

    public OrderRule(IOrderCustomerPermissionService customerPermissionService,
                     IOrderRepository orderRepository) {
        super();
        this.customerPermissionService = customerPermissionService;
        this.orderRepository = orderRepository;
        this.registerRules();
    }

    // 支付前置状态守卫需要「支付前」的旧快照，因此启用旧实体加载机制（见 order-rule-pattern.md §5.3）
    // 说明：规则在领域逻辑执行之后才校验，pay() 已把支付状态改为 PAID，
    // 从当前状态无法区分「首次支付」与「重复支付」，必须依赖持久化态的旧快照。
    @Override
    protected boolean requireOldEntity() {
        return true;
    }

    @Override
    protected Order supplyOldEntity(Order currentModel) {
        if (currentModel.getEntityId() == null) {
            return null;
        }
        return this.orderRepository.findById(currentModel.getEntityId());
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
        // 仅允许取消进行中的订单，且已签收订单不可取消：已取消 / 已关闭 / 已完成 / 已签收的订单不可发起取消。
        // 激活条件叠加「本次工作单元触发了 CANCEL 操作」，保证该规则只在真正执行取消时才校验，
        // 避免订单经支付 / 发货 / 签收推进到其它生命周期状态时被该取消前置条件误拦截。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(this.cancelStatusValid(order))),
                OrderRuleRegistry.ORDER_CANCEL_STATUS_INVALID,
                IActiveRuleCondition.of(order -> order.hasOperation(OrderOperationRegistry.CANCEL)
                        ? ActiveStatus.ACTIVE
                        : ActiveStatus.INACTIVE));
        // 仅允许未发货的订单修改收货地址：订单一旦发货，收货地址不可变更。
        // 激活条件叠加「本次工作单元触发了 CHANGE_ADDRESS 操作」，
        // 保证该规则只在真正执行改址时才校验，避免支付/发货等其它修改命令误激活。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(this.addressChangeStatusValid(order))),
                OrderRuleRegistry.ORDER_ADDRESS_CHANGE_STATUS_INVALID,
                IActiveRuleCondition.of(this::addressChangeRuleActiveStatus));
        // 订单客户信息必填：下单必须关联有效的客户，客户信息不允许为空。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(order.getCustomer() != null)),
                OrderRuleRegistry.ORDER_CUSTOMER_REQUIRED);
        // 收货地址必填：订单必须包含收货地址，不允许缺失配送信息。
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(order.getShippingAddress() != null)),
                OrderRuleRegistry.ORDER_ADDRESS_REQUIRED);
        // 下单用户资格校验：仅在下单一刻激活，校验外部用户是否处于生效状态且具备下单资格。
        // 激活条件由「状态 == 已创建」改为「本次工作单元触发了 PLACE 操作」：
        // 生命周期状态在支付 / 发货后仍为 IN_PROGRESS，已无法据此判断是否为下单一刻。
        this.addRule(
                EntityRule.of(order -> this.customerPermissionService.check(order.getCustomer())),
                OrderRuleRegistry.ORDER_CUSTOMER_QUALIFIED,
                IActiveRuleCondition.of(order -> order.hasOperation(OrderOperationRegistry.PLACE)
                        ? ActiveStatus.ACTIVE
                        : ActiveStatus.INACTIVE));
        // 仅允许待发货且进行中的订单发货：已发货 / 已签收 / 已取消 / 已完成的订单不可再次发货。
        // 支付维度不做限制：待支付与已支付均可发货，以支持货到付款。
        // 与支付守卫同理，ship() 先执行、已把物流状态推进为 SHIPPED，
        // 当前状态无法区分「首次发货」与「重复发货」，必须基于修改前的旧快照判定。
        // 激活条件叠加「本次工作单元触发了 SHIP 操作」，保证该规则只在真正执行发货时才校验。
        this.addRule(
                (order, old) -> RuleCheckResult.of(this.shipStatusValid(old)),
                OrderRuleRegistry.ORDER_SHIP_STATUS_INVALID,
                IActiveRuleCondition.of(this::shipRuleActiveStatus));
        // 仅允许已发货未签收且进行中的订单修正物流信息：未发货 / 已签收 / 已取消的订单不可修正。
        // 修正动作不推进物流状态，此处 old 与当前态等价，仍保持与发货守卫一致的旧快照读法，
        // 避免将来修正动作被赋予状态语义后出现判定偏差。
        this.addRule(
                (order, old) -> RuleCheckResult.of(this.logisticsCorrectionStatusValid(old)),
                OrderRuleRegistry.ORDER_LOGISTICS_CORRECTION_STATUS_INVALID,
                IActiveRuleCondition.of(this::logisticsCorrectionRuleActiveStatus));
        // 物流信息无变化时不产出修正事件：避免重复提交刷出大量无意义的修正事件。
        this.addRule(
                (order, old) -> RuleCheckResult.of(this.logisticsChanged(order, old)),
                OrderRuleRegistry.ORDER_LOGISTICS_CORRECTION_NO_CHANGE,
                IActiveRuleCondition.of(this::logisticsCorrectionRuleActiveStatus));
        // 仅允许已发货且进行中的订单签收：已签收 / 已取消 / 未发货的订单不可发起签收。
        // 与支付守卫同理，sign() 先执行、已把物流与生命周期状态推进为 SIGNED / COMPLETED，
        // 故基于修改前的旧快照判定发货状态，避免当前状态无法反推发货前状态。
        // 激活条件叠加「本次工作单元触发了 SIGN 操作」，保证该规则只在真正执行签收时才校验。
        this.addRule(
                (order, old) -> RuleCheckResult.of(this.signStatusValid(old)),
                OrderRuleRegistry.ORDER_SIGN_STATUS_INVALID,
                IActiveRuleCondition.of(this::signRuleActiveStatus));
        // 仅允许待支付且进行中的订单支付：激活条件叠加「本次工作单元触发了 PAY 操作」，
        // 校验体基于支付前旧快照判定（execute 先执行领域逻辑后校验，当前支付状态已是 PAID），
        // 覆盖「重复支付拦截」与「已取消 / 已关闭订单不可支付」。
        this.addRule(
                (order, old) -> RuleCheckResult.of(this.payStatusValid(old)),
                OrderRuleRegistry.ORDER_PAY_STATUS_INVALID,
                IActiveRuleCondition.of(this::payRuleActiveStatus));
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

    /**
     * 取消前置校验：仅进行中的订单可取消，且已签收订单不可取消（应走售后退货）。
     */
    private boolean cancelStatusValid(Order order) {
        OrderStatus status = order.getStatus();
        if (status == null) {
            return true;
        }
        if (status != OrderStatus.IN_PROGRESS) {
            return false;
        }
        return order.getShipmentStatus() != ShipmentStatus.SIGNED;
    }

    /**
     * 改址前置校验：仅未发货的订单可修改收货地址。
     */
    private boolean addressChangeStatusValid(Order order) {
        ShipmentStatus shipmentStatus = order.getShipmentStatus();
        if (shipmentStatus == null) {
            return true;
        }
        return shipmentStatus == ShipmentStatus.PENDING;
    }

    /**
     * 发货前置校验：基于发货前的旧快照判定，仅待发货且进行中的订单可发货。
     *
     * <p>必须基于旧快照：ship() 已把物流状态推进为 SHIPPED，读当前态无法拦截重复发货。
     * 支付维度不做限制：待支付与已支付均可发货，以支持货到付款。
     * 若将来需要强制「先款后货」，在此追加 {@code paymentStatus == PaymentStatus.PAID} 即可。</p>
     */
    private boolean shipStatusValid(Order oldOrder) {
        if (oldOrder == null) {
            return false;
        }
        if (oldOrder.getStatus() != OrderStatus.IN_PROGRESS) {
            return false;
        }
        if (oldOrder.getShipmentStatus() != ShipmentStatus.PENDING) {
            return false;
        }
        return oldOrder.getPaymentStatus() == PaymentStatus.PAID
                || oldOrder.getPaymentStatus() == PaymentStatus.PENDING;
    }

    /**
     * 修正物流信息前置校验：基于旧快照判定，仅已发货未签收且进行中的订单可修正。
     */
    private boolean logisticsCorrectionStatusValid(Order oldOrder) {
        if (oldOrder == null) {
            return false;
        }
        if (oldOrder.getStatus() != OrderStatus.IN_PROGRESS) {
            return false;
        }
        return oldOrder.getShipmentStatus() == ShipmentStatus.SHIPPED
                || oldOrder.getShipmentStatus() == ShipmentStatus.IN_TRANSIT;
    }

    /**
     * 判定本次修正是否产生了实际变化：与旧快照的物流信息完全一致时视为无变化。
     *
     * <p>LogisticsInfo 继承 ValueObject 并按 equalityComponents 判定结构相等，
     * 因此可直接用 equals 比对trackingNo / companyCode / companyName / shippedAt 四个分量。</p>
     */
    private boolean logisticsChanged(Order order, Order oldOrder) {
        if (oldOrder == null || oldOrder.getLogisticsInfo() == null) {
            return true;
        }
        return !oldOrder.getLogisticsInfo().equals(order.getLogisticsInfo());
    }

    /**
     * 支付前置校验：基于支付前的旧快照判定，仅待支付且进行中的订单可支付。
     *
     * <p>必须叠加生命周期判断：仅判断支付状态会让已取消订单（支付状态仍为待支付）也能支付。</p>
     */
    private boolean payStatusValid(Order oldOrder) {
        if (oldOrder == null) {
            return true;
        }
        return oldOrder.getPaymentStatus() == PaymentStatus.PENDING
                && oldOrder.getStatus() == OrderStatus.IN_PROGRESS;
    }

    /**
     * 签收前置校验：基于签收前的旧快照判定，仅已发货且进行中的订单可签收。
     */
    private boolean signStatusValid(Order oldOrder) {
        if (oldOrder == null) {
            return false;
        }
        return oldOrder.getStatus() == OrderStatus.IN_PROGRESS
                && oldOrder.getShipmentStatus() == ShipmentStatus.SHIPPED;
    }

    private ActiveStatus addressChangeRuleActiveStatus(Order order) {
        if (order.hasOperation(OrderOperationRegistry.CHANGE_ADDRESS)) {
            return ActiveStatus.ACTIVE;
        }
        return ActiveStatus.INACTIVE;
    }

    private ActiveStatus shipRuleActiveStatus(Order order) {
        if (order.hasOperation(OrderOperationRegistry.SHIP)) {
            return ActiveStatus.ACTIVE;
        }
        return ActiveStatus.INACTIVE;
    }

    private ActiveStatus logisticsCorrectionRuleActiveStatus(Order order) {
        if (order.hasOperation(OrderOperationRegistry.CORRECT_LOGISTICS)) {
            return ActiveStatus.ACTIVE;
        }
        return ActiveStatus.INACTIVE;
    }

    private ActiveStatus signRuleActiveStatus(Order order) {
        if (order.hasOperation(OrderOperationRegistry.SIGN)) {
            return ActiveStatus.ACTIVE;
        }
        return ActiveStatus.INACTIVE;
    }

    private ActiveStatus payRuleActiveStatus(Order order) {
        if (order.hasOperation(OrderOperationRegistry.PAY)) {
            return ActiveStatus.ACTIVE;
        }
        return ActiveStatus.INACTIVE;
    }
}
