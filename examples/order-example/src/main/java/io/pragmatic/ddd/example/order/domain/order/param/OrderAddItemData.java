package io.pragmatic.ddd.example.order.domain.order.param;

import io.pragmatic.ddd.base.IParamObject;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import lombok.Data;

/**
 * 订单新增项参数对象，承载新增订单项所需的成组入参。
 *
 * @author wizard-lee
 */
@Data
public class OrderAddItemData implements IParamObject {

    private Long productId;

    private String productName;

    private String spec;

    private Money price;

    private int quantity;
}
