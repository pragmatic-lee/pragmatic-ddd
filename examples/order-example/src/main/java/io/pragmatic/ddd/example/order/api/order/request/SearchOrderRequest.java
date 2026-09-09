package io.pragmatic.ddd.example.order.api.order.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单分页查询条件（对齐 api-contract.md 4.2）。
 * 全部条件均参与过滤；orderId / customerId / trackingNo / status / paymentStatus / shipmentStatus 精确匹配，remark 模糊匹配。
 *
 * @author wizard-lee
 */
@Data
public class SearchOrderRequest {

    /** 订单号（后端 Long 主键，按设计文档 3.5 统一）。 */
    private Long orderId;

    /** 订单生命周期状态：1进行中 2已完成 3已取消 4已关闭（见 OrderStatus）。 */
    private Integer status;

    /** 支付状态：1待支付 2已支付（见 PaymentStatus）。 */
    private Integer paymentStatus;

    /** 物流状态：1待发货 2已发货 3运输中 4已签收 5已拒收 6已退货（见 ShipmentStatus）。 */
    private Integer shipmentStatus;

    /** 客户 ID（精确）。 */
    private Long customerId;

    /** 物流单号（精确）。 */
    private String trackingNo;

    /** 备注（模糊）。 */
    private String remark;

    /** 总金额下限（元）。 */
    private BigDecimal minAmount;

    /** 总金额上限（元）。 */
    private BigDecimal maxAmount;

    /** 支付时间起点（yyyy-MM-dd），含当日。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate payTimeStart;

    /** 支付时间终点（yyyy-MM-dd），含当日。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate payTimeEnd;

    /** 创建时间起点（yyyy-MM-dd），含当日。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeStart;

    /** 创建时间终点（yyyy-MM-dd），含当日。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeEnd;

    /** 页码，从 1 起。 */
    private int pageNo = 1;

    /** 每页条数，默认 10，上限 200。 */
    private int pageSize = 10;
}
