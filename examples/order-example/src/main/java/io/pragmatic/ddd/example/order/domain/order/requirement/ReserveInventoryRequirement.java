package io.pragmatic.ddd.example.order.domain.order.requirement;

import io.pragmatic.ddd.base.IExternalRequirement;

/**
 * 库存预占需求：下单业务成立前必须完成的预占，参数（订单标识、明细）均在订单聚合上。
 * 无字段为常态，天然单例。
 *
 * @author wizard-lee
 */
public record ReserveInventoryRequirement() implements IExternalRequirement {

    public static final ReserveInventoryRequirement INSTANCE = new ReserveInventoryRequirement();

    @Override
    public String code() {
        return "reserve-inventory";
    }
}
