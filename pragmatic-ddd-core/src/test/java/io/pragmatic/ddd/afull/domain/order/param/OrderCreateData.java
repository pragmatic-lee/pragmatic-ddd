package io.pragmatic.ddd.afull.domain.order.param;

import io.pragmatic.ddd.afull.domain.order.model.OrderItem;
import io.pragmatic.ddd.base.IParamObject;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单创建入参容器。
 *
 * @author wizard-lee
 */
public record OrderCreateData(long orderId,
                              String pin,
                              String comment,
                              List<OrderItem> orderItemList,
                              BigDecimal totalPrice) implements IParamObject {
}
