package io.pragmatic.ddd.example.order.domain.order.projection;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单行投影：同源直读的读源形状，字段与本次查询的 SELECT 列一一对应。
 *
 * <p>与 {@link OrderEsProjection}（索引级全量投影）平级，区别只在来源：行投影由
 * resultMap 产出，不是副本，不带 version，也不进对账。目标形状不同时由裁剪器
 * 转为业务子投影（如 {@link OrderSummaryProjection}）。</p>
 *
 * @author wizard-lee
 */
@Data
public class OrderRowProjection implements IOrderProjection {

    /** 订单业务标识，对应 t_order.order_id（数据库自增主键 id 不参与业务查询）。 */
    private Long orderId;

    /** 订单生命周期状态 code，对应 t_order.status。 */
    private int status;

    /** 订单状态名称，由读源按 code 回填，SQL 不写死文案。 */
    private String statusName;

    /** 实付金额（元），由 JSON 列 actual_amount 抽取，单位与聚合同精度。 */
    private BigDecimal actualAmount;

    /** 创建时间，滚动查询的排序键之一（与 orderId 组成唯一且不可变的组合）。 */
    private LocalDateTime createdAt;
}
