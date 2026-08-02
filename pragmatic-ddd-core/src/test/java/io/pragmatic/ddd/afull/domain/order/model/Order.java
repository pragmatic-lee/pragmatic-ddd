package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.afull.domain.order.event.OrderPayedEvent;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单聚合根。
 *
 * @author wizard-lee
 */
public class Order extends AggregateRoot<Long> {

    private final BigDecimal totalPrice;
    private final String comment;
    private final String pin;
    private int status;
    private final List<OrderItem> orderItemList;
    private final LocalDateTime created;


    private Order(long orderId, String pin, String comment, List<OrderItem> orderItemList, BigDecimal totalPrice) {
        this.setEntityId(orderId);
        this.totalPrice = totalPrice;
        this.comment = comment;
        this.pin = pin;
        this.orderItemList = orderItemList;
        this.created = LocalDateTime.now();
        this.markNew();
        //事件收集
        this.collectEvent(() -> new OrderCreatedEvent(this.getEntityId()));
    }

    /**
     * 创建订单：由属性计算领域服务先算出的总价在此赋值，聚合根守护创建不变量。
     */
    public static Order place(long orderId, String pin, String comment,
                              List<OrderItem> orderItemList, BigDecimal totalPrice) {
        return new Order(orderId, pin, comment, orderItemList, totalPrice);
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

