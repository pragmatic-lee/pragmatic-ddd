package io.pragmatic.ddd.afull.domain.order.param;

import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.base.IParamObject;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderInitParam implements IParamObject {

    private long orderId;
    private BigDecimal totalPrice;
    private String comment;
    private String pin;
    private List<OrderItem> orderItemList;
}
