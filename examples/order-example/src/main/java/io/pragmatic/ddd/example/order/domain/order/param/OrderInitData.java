package io.pragmatic.ddd.example.order.domain.order.param;

import io.pragmatic.ddd.base.IParamObject;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import lombok.Data;

import java.util.List;

/**
 * 订单初始化参数对象，承载新建订单所需的成组入参。
 *
 * @author wizard-lee
 */
@Data
public class OrderInitData implements IParamObject {

    private Customer customer;

    private Address shippingAddress;

    private String remark;

    private PaymentMethod paymentMethod;

    private List<OrderItem> orderItems;

    private Money totalAmount;

}
