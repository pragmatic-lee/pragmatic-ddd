package io.pragmatic.ddd.example.order.domain.order.model.valueobject;

import io.pragmatic.ddd.base.ValueObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 客户引用值对象，聚合客户标识与名称。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends ValueObject {

    private Long customerId;

    private String customerName;

    public Customer(Long customerId, String customerName) {
        this.customerId = customerId;
        this.customerName = customerName;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{customerId, customerName};
    }
}
