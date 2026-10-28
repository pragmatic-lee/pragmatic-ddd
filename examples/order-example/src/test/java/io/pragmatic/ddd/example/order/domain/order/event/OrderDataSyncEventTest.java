package io.pragmatic.ddd.example.order.domain.order.event;

import io.pragmatic.ddd.event.DataSyncAction;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.OrderItem;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.param.OrderInitData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单异构数据同步事件测试：验证 upsert / purge 工厂产出，以及数据同步钩子在正常与删除态下的动作分流。
 *
 * @author wizard-lee
 */
@DisplayName("OrderDataSyncEvent")
class OrderDataSyncEventTest {

    @Test
    void upsert_keepsEntityIdAndUpsertAction() {
        Order order = this.newOrder(1001L);

        OrderDataSyncEvent event = OrderDataSyncEvent.upsert(order);

        assertThat(event.getEntityId()).isEqualTo("1001");
        assertThat(event.getDataSyncAction()).isEqualTo(DataSyncAction.UPSERT);
    }

    @Test
    void purge_keepsEntityIdAndPurgeAction() {
        Order order = this.newOrder(1002L);

        OrderDataSyncEvent event = OrderDataSyncEvent.purge(order);

        assertThat(event.getEntityId()).isEqualTo("1002");
        assertThat(event.getDataSyncAction()).isEqualTo(DataSyncAction.PURGE);
    }

    @Test
    void dataSyncHook_normalState_collectsUpsertEventWithVersion() {
        Order order = this.newOrder(1003L);

        order.triggerDataSyncHook();

        OrderDataSyncEvent event = this.findSyncEvent(order).orElseThrow();
        assertThat(event.getDataSyncAction()).isEqualTo(DataSyncAction.UPSERT);
        assertThat(event.getEntityId()).isEqualTo("1003");
        assertThat(event.getVersion()).isNotZero();
    }

    @Test
    void dataSyncHook_deleteState_collectsPurgeEvent() {
        DeletableOrder order = new DeletableOrder(this.newInitData(), 1004L);

        order.markDeleted();
        order.triggerDataSyncHook();

        OrderDataSyncEvent event = this.findSyncEvent(order).orElseThrow();
        assertThat(event.getDataSyncAction()).isEqualTo(DataSyncAction.PURGE);
        assertThat(event.getEntityId()).isEqualTo("1004");
    }

    private Optional<OrderDataSyncEvent> findSyncEvent(Order order) {
        return order.getDomainEvents().stream()
                .filter(e -> e instanceof OrderDataSyncEvent)
                .map(OrderDataSyncEvent.class::cast)
                .findFirst();
    }

    private Order newOrder(Long orderId) {
        return new Order(this.newInitData(), orderId);
    }

    private OrderInitData newInitData() {
        OrderInitData data = new OrderInitData();
        data.setCustomer(new Customer(2001L, "李四"));
        data.setShippingAddress(new Address("广东省", "深圳市", "南山区", "科技园路2号", "李四", "13900000000"));
        data.setRemark("同步事件测试订单");
        data.setPaymentMethod(PaymentMethod.WECHAT);
        data.setTotalAmount(new Money(BigDecimal.valueOf(199), "CNY"));
        data.setOrderItems(List.of(new OrderItem(8001L, "测试商品", "标准", new Money(BigDecimal.valueOf(199), "CNY"), 1)));
        return data;
    }

    /** 测试用订单子类：开放软删标记（基类 setter 为 protected）。 */
    static class DeletableOrder extends Order {

        DeletableOrder(OrderInitData data, Long orderId) {
            super(data, orderId);
        }

        void markDeleted() {
            this.setEntityDelete(true);
        }
    }
}
