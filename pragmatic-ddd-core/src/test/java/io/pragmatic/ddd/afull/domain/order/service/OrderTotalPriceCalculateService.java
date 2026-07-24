package io.pragmatic.ddd.afull.domain.order.service;

import io.pragmatic.ddd.afull.domain.order.model.OrderItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.DoubleStream;

/**
 * 订单总价计算领域服务
 *
 * @author lixiaojing
 * @date 2021/3/1 5:59 下午
 */
public class OrderTotalPriceCalculateService {

    public BigDecimal totalPrice(List<OrderItem> orderItemList, String pin) {
        double totalPrice = orderItemList.stream().map(OrderItem::price).flatMapToDouble(s -> DoubleStream.of(s.doubleValue())).sum();
        return new BigDecimal(totalPrice);

    }
}
