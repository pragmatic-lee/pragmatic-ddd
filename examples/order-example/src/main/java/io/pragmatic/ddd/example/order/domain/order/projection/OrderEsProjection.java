package io.pragmatic.ddd.example.order.domain.order.projection;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单在 Elasticsearch 中的中立投影视图，字段对齐 order 索引 Mapping。
 * 仅作为读模型数据容器，不含任何存储与计算逻辑。
 *
 * @author wizard-lee
 */
@Data
public class OrderEsProjection implements IOrderProjection {

    private Long orderId;

    private int status;

    private String statusName;

    private int paymentMethod;

    private String paymentMethodName;

    private String currency;

    private String remark;

    private String cancelReason;

    private String paymentSerialNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime paidAt;

    private long totalAmount;

    private long platformDiscount;

    private long actualAmount;

    private CustomerProjection customer;

    private AddressProjection shippingAddress;

    private LogisticsProjection logisticsInfo;

    private List<OrderItemProjection> orderItems;

    private List<String> itemProductNames;

    private String itemProductNamesText;

    /** 下单客户信息投影。 */
    @Data
    public static class CustomerProjection {
        private Long customerId;
        private String customerName;
    }

    /** 收货地址投影。 */
    @Data
    public static class AddressProjection {
        private String province;
        private String city;
        private String district;
        private String detail;
        private String receiverName;
        private String receiverPhone;
    }

    /** 物流信息投影。 */
    @Data
    public static class LogisticsProjection {
        private String trackingNo;
        private String companyCode;
        private String companyName;
        private LocalDateTime shippedAt;
    }

    /** 订单明细项投影。 */
    @Data
    public static class OrderItemProjection {
        private Long itemId;
        private Long productId;
        private String productName;
        private String spec;
        private long price;
        private int quantity;
        private long subtotal;
    }
}
