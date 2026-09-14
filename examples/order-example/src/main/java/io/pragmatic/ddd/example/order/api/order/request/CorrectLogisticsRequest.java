package io.pragmatic.ddd.example.order.api.order.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 修正订单物流信息请求：用于已发货未签收区间的物流信息纠错。
 * 只替换物流信息，不推进物流状态；shippedAt 为空时沿用原发货时间。
 */
@Data
public class CorrectLogisticsRequest {

    private String companyCode;

    private String companyName;

    private String trackingNo;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime shippedAt;

    private String reason;
}
