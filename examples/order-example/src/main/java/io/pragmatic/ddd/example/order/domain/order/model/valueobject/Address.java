package io.pragmatic.ddd.example.order.domain.order.model.valueobject;

import io.pragmatic.ddd.base.ValueObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 收货地址值对象，整体序列化为订单表一列。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends ValueObject {

    private String province;

    private String city;

    private String district;

    private String detail;

    private String receiverName;

    private String receiverPhone;

    public Address(String province, String city, String district, String detail, String receiverName, String receiverPhone) {
        this.province = province;
        this.city = city;
        this.district = district;
        this.detail = detail;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{province, city, district, detail, receiverName, receiverPhone};
    }
}
