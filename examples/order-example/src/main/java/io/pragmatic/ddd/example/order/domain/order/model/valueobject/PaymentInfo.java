package io.pragmatic.ddd.example.order.domain.order.model.valueobject;

import io.pragmatic.ddd.base.ValueObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 支付信息值对象，内聚支付流水号、支付平台优惠金额与实付金额。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentInfo extends ValueObject {

    private String paymentSerialNo;

    private Money platformDiscount;

    private Money actualAmount;

    public PaymentInfo(String paymentSerialNo, Money platformDiscount, Money actualAmount) {
        this.paymentSerialNo = paymentSerialNo;
        this.platformDiscount = platformDiscount;
        this.actualAmount = actualAmount;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{paymentSerialNo, platformDiscount, actualAmount};
    }
}
