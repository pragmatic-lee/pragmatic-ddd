package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 订单物流信息已修正领域事件，携带改前 / 改后的运单号与物流公司编码及修正原因，用于审计留痕。
 * 仅在已发货未签收区间由纠错动作触发，不表达物流状态推进（状态推进见 OrderShippedEvent）。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLogisticsCorrectedEvent extends BaseDomainEvent {

    private String beforeTrackingNo;

    private String afterTrackingNo;

    private String beforeCompanyCode;

    private String afterCompanyCode;

    private String reason;

    public OrderLogisticsCorrectedEvent(String entityId) {
        super(entityId);
    }

    /**
     * 基于聚合当前态与改前快照构建事件。
     *
     * @param order  修正后的订单聚合
     * @param before 修正前的物流信息快照，为空时改前字段留空
     * @param reason 修正原因
     */
    public static OrderLogisticsCorrectedEvent buildEvent(Order order,
                                                          LogisticsInfo before,
                                                          String reason) {
        OrderLogisticsCorrectedEvent event =
                new OrderLogisticsCorrectedEvent(order.getEntityId().toString());
        if (before != null) {
            event.setBeforeTrackingNo(before.getTrackingNo());
            event.setBeforeCompanyCode(before.getCompanyCode());
        }
        LogisticsInfo after = order.getLogisticsInfo();
        if (after != null) {
            event.setAfterTrackingNo(after.getTrackingNo());
            event.setAfterCompanyCode(after.getCompanyCode());
        }
        event.setReason(reason);
        return event;
    }
}
