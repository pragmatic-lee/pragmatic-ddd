package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.base.IEnumValue;

/**
 * 订单状态枚举，消除魔法值。
 *
 * @author wizard-lee
 */
public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {

    CREATED(1, "已创建"),
    PAYED(3, "已支付"),
    CANCELLED(9, "已取消");

    private final Integer value;
    private final String name;

    OrderStatus(Integer value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override
    public Integer getValue() {
        return this.value;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
