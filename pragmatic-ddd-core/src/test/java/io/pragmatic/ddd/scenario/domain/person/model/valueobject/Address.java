package io.pragmatic.ddd.scenario.domain.person.model.valueobject;

import io.pragmatic.ddd.base.ValueObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 地址值对象。无标识、不可变，基于结构相等性（equalityComponents）。
 *
 * @author wizard-lee
 */
@Getter
@AllArgsConstructor
public class Address extends ValueObject {

    private final String province;
    private final String city;
    private final String district;
    private final String detail;

    public String fullAddress() {
        return String.join("", province, city, district, detail);
    }

    protected Address() {
        this.province = null;
        this.city = null;
        this.district = null;
        this.detail = null;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{province, city, district, detail};
    }
}
