package io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.reducer.IOrderSummaryReducer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 订单索引级全量投影到概要投影的裁剪器实现，对应领域契约 {@link IOrderSummaryReducer}。
 * 裁掉明细项与物流等非列表字段，并把 ES 文档中的嵌套字段
 * {@code customer.customerName} 提升为概要投影的顶层字段。
 *
 * <p>层级提升是存储侧 {@code _source} 过滤无法表达的——后者只能裁剪字段路径，
 * 不能改变字段层级，故必须由本裁剪器在 Java 内存中完成。</p>
 *
 * <p>本类为纯函数实现：无状态、无存储访问，可独立单测。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderSummaryReducer implements IOrderSummaryReducer {

    /**
     * 返回本裁剪器的源投影类型，即索引 order_index 的索引级全量投影。
     *
     * @return 订单 ES 全量投影类型
     */
    @Override
    public Class<OrderEsProjection> sourceType() {
        return OrderEsProjection.class;
    }

    /**
     * 返回本裁剪器产出的子投影类型。
     *
     * @return 订单概要投影类型
     */
    @Override
    public Class<OrderSummaryProjection> projectionType() {
        return OrderSummaryProjection.class;
    }

    /**
     * 将订单全量投影裁剪为概要投影，含客户名称的层级提升。
     *
     * @param source 订单 ES 全量投影；为 null 时返回 null
     * @return 订单概要投影
     */
    @Override
    public OrderSummaryProjection reduce(OrderEsProjection source) {
        if (source == null) {
            return null;
        }
        OrderSummaryProjection summary = new OrderSummaryProjection();
        summary.setOrderId(source.getOrderId());
        summary.setStatus(source.getStatus());
        summary.setStatusName(source.getStatusName());
        summary.setActualAmount(source.getActualAmount());
        summary.setCreatedAt(source.getCreatedAt());
        Optional.ofNullable(source.getCustomer())
                .map(OrderEsProjection.CustomerProjection::getCustomerName)
                .ifPresent(summary::setCustomerName);
        return summary;
    }
}
