package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.afull.domain.order.operation.OrderOperationRegistry;
import io.pragmatic.ddd.afull.domain.order.param.OrderCreateData;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.operation.OperationRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 订单聚合根。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AggregateRoot<Long> {

    private BigDecimal totalPrice;
    private String comment;
    private String pin;
    private OrderStatus status;
    private List<OrderItem> orderItemList;

    /** 业务构造函数：属性赋值 + 记录 CREATE 操作 + 审计标记 + 收集初始事件。 */
    public Order(OrderCreateData data) {
        this.setEntityId(data.orderId());
        this.pin = data.pin();
        this.comment = data.comment();
        this.orderItemList = data.orderItemList();
        this.totalPrice = data.totalPrice();
        this.status = OrderStatus.CREATED;
        this.markNew();
        this.markCreated();
        this.recordOperation(OrderOperationRegistry.CREATE);
        this.collectEvent(() -> OrderCreatedEvent.buildEvent(this));
    }

    /** 订单支付：状态流转为已支付。 */
    public void payment() {
        this.status = OrderStatus.PAYED;
        this.markModified();
        this.recordOperation(OrderOperationRegistry.PAYMENT);
        this.collectEvent(OrderPayedEvent.buildEvent(this));
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderBrokenRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperationRegistry.INSTANCE;
    }

    public List<OrderItem> getOrderItemList() {
        return Collections.unmodifiableList(this.orderItemList);
    }
}
