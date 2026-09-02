package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.repository.query.AbstractAggregateProjector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 订单聚合到 Redis 缓存副本投影的纯映射实现，独立映射、不复用 ES 投影逻辑。
 * 与 {@link OrderEsProjector} 平级，仅取聚合原生字段，不派生 ES 检索专用字段。
 *
 * @author wizard-lee
 */
@Component
public class OrderCacheProjector extends AbstractAggregateProjector<Order, OrderCacheProjection> {

    public OrderCacheProjector() {
        super(OrderCacheProjection.class);
    }

    /**
     * 将订单聚合映射为 Redis 缓存副本投影（剔除 ES 检索派生字段）。
     *
     * @param order 待投影的订单聚合
     * @return 订单在 Redis 中的缓存副本投影
     */
    @Override
    public OrderCacheProjection project(Order order) {
        OrderCacheProjection projection = new OrderCacheProjection();
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

        projection.setVersion(order.getOldVersion());

        Optional.ofNullable(order.getCustomer())
                .ifPresent(customer -> {
                    OrderCacheProjection.CustomerProjection cp = new OrderCacheProjection.CustomerProjection();
                    cp.setCustomerId(customer.getCustomerId());
                    cp.setCustomerName(customer.getCustomerName());
                    projection.setCustomer(cp);
                });

        Optional.ofNullable(order.getShippingAddress())
                .ifPresent(address -> {
                    OrderCacheProjection.AddressProjection ap = new OrderCacheProjection.AddressProjection();
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
                    OrderCacheProjection.LogisticsProjection lp = new OrderCacheProjection.LogisticsProjection();
                    lp.setTrackingNo(logistics.getTrackingNo());
                    lp.setCompanyCode(logistics.getCompanyCode());
                    lp.setCompanyName(logistics.getCompanyName());
                    lp.setShippedAt(logistics.getShippedAt());
                    projection.setLogisticsInfo(lp);
                });

        List<OrderCacheProjection.OrderItemProjection> items = order.getOrderItems().getAllItems().stream()
                .map(this::toItemProjection)
                .collect(Collectors.toList());
        projection.setOrderItems(items);

        return projection;
    }

    private OrderCacheProjection.OrderItemProjection toItemProjection(OrderItem item) {
        OrderCacheProjection.OrderItemProjection ip = new OrderCacheProjection.OrderItemProjection();
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
