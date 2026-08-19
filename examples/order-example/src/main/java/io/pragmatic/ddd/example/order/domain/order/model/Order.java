package io.pragmatic.ddd.example.order.domain.order.model;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.param.OrderInitData;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.PaymentInfo;
import io.pragmatic.ddd.example.order.domain.order.operation.OrderOperation;
import io.pragmatic.ddd.example.order.domain.order.operation.OrderOperationRegistry;
import io.pragmatic.ddd.example.order.domain.order.rule.OrderRuleRegistry;
import io.pragmatic.ddd.example.order.domain.order.event.OrderAddressChangedEvent;
import io.pragmatic.ddd.example.order.domain.order.event.OrderCancelledEvent;
import io.pragmatic.ddd.example.order.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.example.order.domain.order.event.OrderPaidEvent;
import io.pragmatic.ddd.example.order.domain.order.event.OrderShippedEvent;
import io.pragmatic.ddd.track.TrackedList;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 订单聚合根，封装订单完整生命周期与不变性约束。
 *
 * 业务方法仅做状态赋值，不内嵌校验与计算逻辑，订单金额由外部计算后传入，
 * 窗口期、发货与支付前置不变性等校验职责外移应用层。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AggregateRoot<Long> {

    /**
     * 下单客户。
     */
    private Customer customer;

    /**
     * 订单状态。
     */
    private OrderStatus status;

    /**
     * 订单项集合，支持变更追踪。
     */
    private TrackedList<OrderItem, Long> orderItems;

    /**
     * 收货地址。
     */
    private Address shippingAddress;

    /**
     * 订单金额币种。
     */
    private String currency;

    /**
     * 订单总金额。
     */
    private Money totalAmount;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 支付时间
     */
    private LocalDateTime paidAt;

    /**
     * 取消原因
     */
    private String cancelReason;

    /**
     * 支付方式
     */
    private PaymentMethod paymentMethod;

    /**
     * 发货物流信息
     */
    private LogisticsInfo logisticsInfo;

    /**
     * 支付流水号
     */
    private String paymentSerialNo;

    /**
     * 支付平台优惠金额
     */
    private Money platformDiscount;

    /**
     * 实付金额
     */
    private Money actualAmount;

    /**
     * 基于初始化数据创建订单并持有初始订单项，发布订单创建事件。
     *
     * @param data 订单初始化参数
     * @param orderId 订单标识
     */
    public Order(OrderInitData data,Long orderId) {
        this.setEntityId(orderId);
        this.customer = data.getCustomer();
        this.shippingAddress = data.getShippingAddress();
        this.remark = data.getRemark();
        this.paymentMethod = data.getPaymentMethod();
        this.status = OrderStatus.CREATED;
        this.currency = "CNY";
        this.orderItems = new TrackedList<>(data.getOrderItems() == null ? List.of() : data.getOrderItems());
        this.recordOperation(OrderOperation.PLACE);
        this.markCreated();
        this.markNew();
        this.collectEvent(OrderCreatedEvent.buildEvent(this));
    }

    /**
     * 添加订单项，若同商品已存在则合并数量，并刷新订单总金额。
     *
     * @param item 待添加订单项
     * @param totalAmount 外部计算后的订单总金额
     */
    public void addItem(OrderItem item, Money totalAmount) {
        Optional<OrderItem> existItem = this.orderItems.getAllItems().stream()
                .filter(exist -> exist.getProductId().equals(item.getProductId()))
                .findFirst();
        if (existItem.isPresent()) {
            OrderItem merged = existItem.get().merge(item);
            this.orderItems.removeItems(i -> i.id().equals(existItem.get().id()));
            this.orderItems.append(merged);
        } else {
            this.orderItems.append(item);
        }
        this.totalAmount = totalAmount;
        this.markModified();
        this.recordOperation(OrderOperation.ADD_ITEM);
    }

    /**
     * 更新指定订单项的数量，并刷新订单总金额。
     *
     * @param itemId 订单项标识
     * @param quantity 更新后的数量
     * @param totalAmount 外部计算后的订单总金额
     */
    public void updateItem(Long itemId, int quantity, Money totalAmount) {
        this.findItem(itemId).ifPresent(old -> {
            OrderItem updated = old.withQuantity(quantity);
            this.orderItems.removeItems(i -> i.id().equals(itemId));
            this.orderItems.append(updated);
        });
        this.totalAmount = totalAmount;
        this.markModified();
        this.recordOperation(OrderOperation.UPDATE_ITEM);
    }

    /**
     * 移除指定订单项，并刷新订单总金额。
     *
     * @param itemId 订单项标识
     * @param totalAmount 外部计算后的订单总金额
     */
    public void removeItem(Long itemId, Money totalAmount) {
        this.orderItems.removeItems(i -> i.id().equals(itemId));
        this.totalAmount = totalAmount;
        this.markModified();
        this.recordOperation(OrderOperation.REMOVE_ITEM);
    }

    /**
     * 变更收货地址，并发布收货地址变更事件。
     *
     * @param shippingAddress 新的收货地址
     */
    public void changeAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
        this.markModified();
        this.recordOperation(OrderOperation.CHANGE_ADDRESS);
        this.collectEvent(OrderAddressChangedEvent.buildEvent(this));
    }

    /**
     * 标记订单已发货并记录物流信息，并发布订单发货事件。
     *
     * @param logisticsInfo 物流信息
     */
    public void ship(LogisticsInfo logisticsInfo) {
        this.status = OrderStatus.SHIPPED;
        this.logisticsInfo = logisticsInfo;
        this.markModified();
        this.recordOperation(OrderOperation.SHIP);
        this.collectEvent(OrderShippedEvent.buildEvent(this));
    }

    /**
     * 标记订单已支付并记录支付信息，并发布订单支付事件。
     *
     * @param paymentInfo 支付信息
     */
    public void pay(PaymentInfo paymentInfo) {
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.paymentSerialNo = paymentInfo.getPaymentSerialNo();
        this.platformDiscount = paymentInfo.getPlatformDiscount();
        this.actualAmount = paymentInfo.getActualAmount();
        this.markModified();
        this.recordOperation(OrderOperation.PAY);
        this.collectEvent(OrderPaidEvent.buildEvent(this));
    }

    /**
     * 取消订单并记录取消原因，并发布订单取消事件。
     *
     * @param reason 取消原因
     */
    public void cancel(String reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
        this.markModified();
        this.recordOperation(OrderOperation.CANCEL);
        this.collectEvent(OrderCancelledEvent.buildEvent(this));
    }

    private Optional<OrderItem> findItem(Long itemId) {
        return this.orderItems.getAllItems().stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst();
    }
    @Override
    protected OrderRuleRegistry brokenRuleRegistry() {
        return OrderRuleRegistry.getInstance();
    }

    @Override
    protected OrderOperationRegistry operationRegistry() {
        return OrderOperationRegistry.getInstance();
    }
}
