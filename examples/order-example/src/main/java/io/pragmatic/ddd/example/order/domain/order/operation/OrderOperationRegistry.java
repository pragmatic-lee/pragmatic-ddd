package io.pragmatic.ddd.example.order.domain.order.operation;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * 订单聚合操作码注册表，集中声明全部业务操作码。
 *
 * <p>操作常量以 {@code static EntityOperation} 声明在本最终子类上，
 * 由基类 {@link OperationRegistry} 构造时的反射自动扫描注册。</p>
 *
 * @author wizard-lee
 */
public class OrderOperationRegistry extends OperationRegistry {

    public static final EntityOperation PLACE = EntityOperation.of("PLACE", "下单");

    public static final EntityOperation PAY = EntityOperation.of("PAY", "支付");

    public static final EntityOperation CANCEL = EntityOperation.of("CANCEL", "取消");

    public static final EntityOperation CHANGE_ADDRESS = EntityOperation.of("CHANGE_ADDRESS", "变更收货地址");

    public static final EntityOperation ADD_ITEM = EntityOperation.of("ADD_ITEM", "新增订单项");

    public static final EntityOperation REMOVE_ITEM = EntityOperation.of("REMOVE_ITEM", "移除订单项");

    public static final EntityOperation UPDATE_ITEM = EntityOperation.of("UPDATE_ITEM", "更新订单项");

    public static final EntityOperation SHIP = EntityOperation.of("SHIP", "发货");

    public static final OrderOperationRegistry INSTANCE = new OrderOperationRegistry();

    private OrderOperationRegistry() {
    }
}
