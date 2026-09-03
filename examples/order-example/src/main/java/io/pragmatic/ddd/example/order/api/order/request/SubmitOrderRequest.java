package io.pragmatic.ddd.example.order.api.order.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提交订单请求（对齐 api-contract.md 4.1）。
 * customerId 由登录上下文注入，请求体不传。
 *
 * @author wizard-lee
 */
@Data
public class SubmitOrderRequest {

    /** 下单商品项。 */
    private List<Item> items;

    /** 收货地址。 */
    private Address address;

    /** 商品项。 */
    @Data
    public static class Item {

        /** 商品 ID（按 order-backend-api-design.md 3.5 统一为 Long）。 */
        private Long productId;

        /** 商品名称快照。 */
        private String productName;

        /** 单价（元）。 */
        private BigDecimal price;

        /** 数量。 */
        private Integer quantity;
    }

    /** 收货地址（与订单收货地址字段同构）。 */
    @Data
    public static class Address {

        private String province;

        private String city;

        private String district;

        private String detail;

        private String receiverName;

        private String receiverPhone;
    }
}
