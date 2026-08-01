package io.pragmatic.ddd.afull.domain.order.event;

/**
 * 订单领域事件订阅者别名常量。
 *
 * @author wizard-lee
 */
public final class OrderEventSubscriberAliases {

    private OrderEventSubscriberAliases() {}

    /** 发送短信通知（订单创建触发） */
    public static final String SEND_SMS_ON_ORDER_CREATED = "sendSMS-on-order-created";

    /** 通知仓库（订单创建触发） */
    public static final String NOTICE_WAREHOUSE_ON_ORDER_CREATED = "noticeWarehouse-on-order-created";

    /** 发送短信通知（订单支付触发） */
    public static final String SEND_SMS_ON_ORDER_PAYED = "sendSMS-on-order-payed";

    /** 通知仓库（订单支付触发） */
    public static final String NOTICE_WAREHOUSE_ON_ORDER_PAYED = "noticeWarehouse-on-order-payed";
}
