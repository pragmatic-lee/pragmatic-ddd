package io.pragmatic.ddd.example.order.application.order.input;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单支付入参：业务语义、与协议无关、自有扁平结构。
 * 不引用领域值对象 PaymentInfo / Money，由 OrderPayUpdater 转换为领域 PaymentInfo。
 * 金额使用 BigDecimal，币种唯一（currency），平台优惠与实付金额共用同一币种。
 *
 * @author wizard-lee
 */
@Data
public class PayOrderInput {

    /**
     * 支付流水号。
     */
    private String paymentSerialNo;

    /**
     * 币种。
     */
    private String currency;

    /**
     * 实付金额（优惠后，由调用方算好传入）。
     */
    private BigDecimal amount;

    /**
     * 支付平台优惠金额。
     */
    private BigDecimal platformDiscountAmount;
}
