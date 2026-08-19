package io.pragmatic.ddd.example.order.domain.order.model;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.track.ITrackable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单项实体，聚合单项商品、金额与数量，subtotal 由 price 与 quantity 派生。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends AbstractEntity<Long> implements ITrackable<Long> {

    private Long productId;

    private String productName;

    private String spec;

    private Money price;

    private int quantity;

    private Money subtotal;

    public OrderItem(Long productId, String productName, String spec, Money price, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.spec = spec;
        this.price = price;
        this.quantity = quantity;
        this.subtotal = price.multiply(quantity);
        this.markCreated();
    }

    public void changeQuantity(int quantity) {
        this.quantity = quantity;
        this.subtotal = price.multiply(quantity);
        this.markModified();
    }

    /**
     * 以当前订单项为基底，合并另一订单项的库存数量，返回保留本项身份标识的新订单项。
     *
     * @param other 被合并的订单项
     * @return 合并后的新订单项
     */
    public OrderItem merge(OrderItem other) {
        OrderItem merged = new OrderItem(productId, productName, spec, price,
                quantity + other.getQuantity());
        merged.assignEntityId(getEntityId());
        return merged;
    }

    /**
     * 以当前订单项为基底，调整为指定数量，返回保留本项身份标识的新订单项。
     *
     * @param quantity 调整后的数量
     * @return 数量调整后的新订单项
     */
    public OrderItem withQuantity(int quantity) {
        OrderItem updated = new OrderItem(productId, productName, spec, price, quantity);
        updated.assignEntityId(getEntityId());
        return updated;
    }

    public void changePrice(Money price) {
        this.price = price;
        this.subtotal = price.multiply(quantity);
        this.markModified();
    }

    @Override
    public Long id() {
        return this.getEntityId();
    }

    void assignEntityId(Long entityId) {
        this.setEntityId(entityId);
    }
}
