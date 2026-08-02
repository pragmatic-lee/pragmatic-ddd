package io.pragmatic.ddd.afull.domain.order.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 订单总价计算上下文：承载计算所需的全部领域输入。
 *
 * @author wizard-lee
 */
@Getter
@Builder
public class TotalPriceContext {

    /** 订单项列表 */
    private final List<OrderItem> orderItemList;

    /** 用户 PIN */
    private final String pin;
}
