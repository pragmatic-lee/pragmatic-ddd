package io.pragmatic.ddd.example.order.api.order.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class ShipOrderRequest {

    private String companyCode;

    private String companyName;

    private String trackingNo;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime shippedAt;
}
