package io.pragmatic.ddd.example.order.domain.order.model.valueobject;

import io.pragmatic.ddd.base.ValueObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 物流信息值对象，内聚物流单号、物流公司编码、物流公司名称与发货时间。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogisticsInfo extends ValueObject {

    private String trackingNo;

    private String companyCode;

    private String companyName;

    private LocalDateTime shippedAt;

    public LogisticsInfo(String trackingNo, String companyCode, String companyName, LocalDateTime shippedAt) {
        this.trackingNo = trackingNo;
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.shippedAt = shippedAt;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{trackingNo, companyCode, companyName, shippedAt};
    }
}
