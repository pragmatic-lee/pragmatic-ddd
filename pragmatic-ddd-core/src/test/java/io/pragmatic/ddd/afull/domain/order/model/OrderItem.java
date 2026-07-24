package io.pragmatic.ddd.afull.domain.order.model;

import java.math.BigDecimal;

/**
 * @author lixiaojing
 * @date 2021/3/1 5:26 下午
 */
public class OrderItem {


    private final int number;
    private final BigDecimal price;

    public OrderItem(long skuId, int number, BigDecimal price) {
        this.number = number;
        this.price = price;
    }

    public BigDecimal price() {
        return this.price.multiply(new BigDecimal(number));
    }
}
