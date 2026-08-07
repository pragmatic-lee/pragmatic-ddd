package io.pragmatic.ddd.afull.api.order;

import java.math.BigDecimal;

/**
 * 订单明细项的数据传输对象。
 *
 * @author wizard-lee
 */
public class OrderItemDto {
    public long skuId;
    public int number;
    public BigDecimal price;
}
