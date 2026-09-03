package io.pragmatic.ddd.example.order.api.order.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单支付请求（对齐 api-contract.md 4.4）。
 * payMethod 字典：1支付宝 2微信支付 3银联 4余额；
 * 后端支付方式枚举尚未按契约收敛（WECHAT=1/ALIPAY=2），字典统一前仅接收、不做一致性强校验
 * （order-backend-api-design.md 拍板 #4）。
 *
 * @author wizard-lee
 */
@Data
public class PayOrderRequest {

    /** 订单 ID（需与路径参数一致）。 */
    private Long orderId;

    /** 支付方式。 */
    private Integer payMethod;

    /** 支付流水号。 */
    private String payTransactionNo;

    /** 平台优惠金额（元，无优惠给 0）。 */
    private BigDecimal platformDiscountAmount;

    /** 实付金额（元）。 */
    private BigDecimal actualAmount;
}
