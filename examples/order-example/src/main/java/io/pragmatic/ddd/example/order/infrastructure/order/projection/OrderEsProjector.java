package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.repository.query.projection.AbstractAggregateProjector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 订单聚合到 ES 投影的纯映射实现，负责计算派生字段。
 *
 * @author wizard-lee
 */
@Component
public class OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> {

    public OrderEsProjector() {
        super(OrderEsProjection.class);
    }

    /**
     * 将订单聚合映射为 ES 中立投影，并派生商品名称列表等查询辅助字段。
     *
     * @param order 待投影的订单聚合
     * @return 订单在 ES 中的投影视图
     */
    @Override
    public OrderEsProjection project(Order order) {
        OrderEsProjection projection = new OrderEsProjection();
        projection.setOrderId(order.getEntityId());
        projection.setStatus(order.getStatus().getValue());
        projection.setStatusName(order.getStatus().name());
        projection.setPaymentMethod(order.getPaymentMethod().getValue());
        projection.setPaymentMethodName(order.getPaymentMethod().name());
        projection.setCurrency(order.getCurrency());
        projection.setRemark(order.getRemark());
        projection.setCancelReason(order.getCancelReason());
        projection.setPaymentSerialNo(order.getPaymentSerialNo());
        projection.setCreatedAt(order.getCreatedAt());
        projection.setUpdatedAt(order.getUpdatedAt());
        projection.setPaidAt(order.getPaidAt());

        projection.setTotalAmount(toFen(order.getTotalAmount()));
        projection.setPlatformDiscount(toFen(order.getPlatformDiscount()));
        projection.setActualAmount(toFen(order.getActualAmount()));

        Optional.ofNullable(order.getCustomer())
                .ifPresent(customer -> {
                    OrderEsProjection.CustomerProjection cp = new OrderEsProjection.CustomerProjection();
                    cp.setCustomerId(customer.getCustomerId());
                    cp.setCustomerName(customer.getCustomerName());
                    projection.setCustomer(cp);
                });

        Optional.ofNullable(order.getShippingAddress())
                .ifPresent(address -> {
                    OrderEsProjection.AddressProjection ap = new OrderEsProjection.AddressProjection();
                    ap.setProvince(address.getProvince());
                    ap.setCity(address.getCity());
                    ap.setDistrict(address.getDistrict());
                    ap.setDetail(address.getDetail());
                    ap.setReceiverName(address.getReceiverName());
                    ap.setReceiverPhone(address.getReceiverPhone());
                    projection.setShippingAddress(ap);
                });

        Optional.ofNullable(order.getLogisticsInfo())
                .ifPresent(logistics -> {
                    OrderEsProjection.LogisticsProjection lp = new OrderEsProjection.LogisticsProjection();
                    lp.setTrackingNo(logistics.getTrackingNo());
                    lp.setCompanyCode(logistics.getCompanyCode());
                    lp.setCompanyName(logistics.getCompanyName());
                    lp.setShippedAt(logistics.getShippedAt());
                    projection.setLogisticsInfo(lp);
                });

        List<OrderEsProjection.OrderItemProjection> items = order.getOrderItems().getAllItems().stream()
                .map(this::toItemProjection)
                .collect(Collectors.toList());
        projection.setOrderItems(items);

        List<String> productNames = items.stream()
                .map(OrderEsProjection.OrderItemProjection::getProductName)
                .collect(Collectors.toList());
        projection.setItemProductNames(productNames);
        projection.setItemProductNamesText(String.join(" ", productNames));

        return projection;
    }

    private OrderEsProjection.OrderItemProjection toItemProjection(OrderItem item) {
        OrderEsProjection.OrderItemProjection ip = new OrderEsProjection.OrderItemProjection();
        ip.setItemId(item.getEntityId());
        ip.setProductId(item.getProductId());
        ip.setProductName(item.getProductName());
        ip.setSpec(item.getSpec());
        ip.setPrice(toFen(item.getPrice()));
        ip.setQuantity(item.getQuantity());
        ip.setSubtotal(toFen(item.getSubtotal()));
        return ip;
    }

    private long toFen(Money money) {
        return Optional.ofNullable(money)
                .map(Money::getAmount)
                .map(value -> value.multiply(java.math.BigDecimal.valueOf(100)).longValue())
                .orElse(0L);
    }
}
