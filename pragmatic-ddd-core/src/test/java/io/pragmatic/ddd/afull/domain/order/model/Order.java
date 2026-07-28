package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.afull.domain.order.param.OrderInitParam;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author lixiaojing
 */
public class Order extends AggregateRoot<Long> {

    private final BigDecimal totalPrice;
    private final String comment;
    private final String pin;
    private int status;
    private final List<OrderItem> orderItemList;
    private final LocalDateTime created;


    public Order(OrderInitParam orderInitParam) {
        this.setEntityId(orderInitParam.getOrderId());
        this.totalPrice = orderInitParam.getTotalPrice();
        this.comment = orderInitParam.getComment();
        this.pin = orderInitParam.getPin();
        this.orderItemList = orderInitParam.getOrderItemList();
        this.created = LocalDateTime.now();
        //事件收集
        this.collectEvent(() -> new OrderCreatedEvent(this.getEntityId()));
    }


    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderBrokenRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return null;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public int getStatus() {
        return status;
    }

    public List<OrderItem> getOrderItemList() {
        return orderItemList;
    }



    /**
     * 订单支持业务操作
     *
     * @return 返回订单已支持事件
     */
    public void payment() {
        this.status = 3;
        //事件收集
        this.collectEvent(new OrderPayedEvent(this.getEntityId()));

    }

    public String getComment() {
        return comment;
    }

    public String getPin() {
        return pin;
    }

    public LocalDateTime getCreated() {
        return created;
    }

}

