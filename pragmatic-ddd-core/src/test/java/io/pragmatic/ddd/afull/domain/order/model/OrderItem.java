package io.pragmatic.ddd.afull.domain.order.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 订单项。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    private long skuId;
    private int number;
    private BigDecimal price;

    public OrderItem(long skuId, int number, BigDecimal price) {
        this.skuId = skuId;
        this.number = number;
        this.price = price;
    }

    /** 计算该订单项小计金额。 */
    public BigDecimal price() {
        return this.price.multiply(BigDecimal.valueOf(this.number));
    }
}
