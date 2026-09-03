package io.pragmatic.ddd.example.order.api.order.request;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 订单分页查询条件（对齐 api-contract.md 4.2）。
 * 当前版本仅 orderId/status/customerId 参与过滤（底层 OrderPageQuery 现有能力）；
 * trackingNo/remark/金额区间/时间区间待查询能力扩展（order-backend-api-design.md 4.2）后启用。
 *
 * @author wizard-lee
 */
@Data
public class SearchOrderRequest {

    /** 订单号（后端 Long 主键，按设计文档 3.5 统一）。 */
    private Long orderId;

    /** 订单状态：1待支付 2已支付 3已发货 4已完成 5已取消；枚举字典统一前 1~3 生效。 */
    private Integer status;

    /** 客户 ID。 */
    private Long customerId;

    /** 物流单号（模糊），待查询能力扩展。 */
    private String trackingNo;

    /** 备注（模糊），待查询能力扩展。 */
    private String remark;

    /** 总金额下限（元），待查询能力扩展。 */
    private BigDecimal minAmount;

    /** 总金额上限（元），待查询能力扩展。 */
    private BigDecimal maxAmount;

    /** 支付时间起点（yyyy-MM-dd），待查询能力扩展。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate payTimeStart;

    /** 支付时间终点（yyyy-MM-dd），待查询能力扩展。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate payTimeEnd;

    /** 创建时间起点（yyyy-MM-dd），待查询能力扩展。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeStart;

    /** 创建时间终点（yyyy-MM-dd），待查询能力扩展。 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createTimeEnd;

    /** 页码，从 1 起。 */
    private int pageNo = 1;

    /** 每页条数，默认 10，上限 200。 */
    private int pageSize = 10;
}
