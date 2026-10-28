package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica.OrderEsSource;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 订单 ES 投影订阅测试：验证同步动作分流（PURGE 直清、UPSERT 聚合缺失转清、UPSERT 正常同步）。
 *
 * @author wizard-lee
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderDataSyncEsProjectionHandle")
class OrderDataSyncEsProjectionHandleTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEsSource esSource;

    private OrderDataSyncEsProjectionHandle handle;

    @BeforeEach
    void setUp() {
        this.handle = new OrderDataSyncEsProjectionHandle(this.orderRepository, this.esSource);
    }

    @Test
    void purgeAction_purgesReplicaWithoutLoadingAggregate() {
        Order orderRef = this.eventRef(1L);

        this.handle.handleEvent(OrderDataSyncEvent.purge(orderRef));

        verify(this.esSource).purge(1L);
        verify(this.esSource, never()).sync(any());
        verifyNoInteractions(this.orderRepository);
    }

    @Test
    void upsertAction_aggregateMissing_purgesReplica() {
        Order orderRef = this.eventRef(2L);
        when(this.orderRepository.findById(2L)).thenReturn(null);

        this.handle.handleEvent(OrderDataSyncEvent.upsert(orderRef));

        verify(this.esSource).purge(2L);
        verify(this.esSource, never()).sync(any());
    }

    @Test
    void upsertAction_aggregatePresent_syncsReplica() {
        Order orderRef = this.eventRef(3L);
        Order loaded = mock(Order.class);
        when(this.orderRepository.findById(3L)).thenReturn(loaded);

        this.handle.handleEvent(OrderDataSyncEvent.upsert(orderRef));

        verify(this.esSource).sync(loaded);
        verify(this.esSource, never()).purge(any());
    }

    /** 仅用于事件构造的聚合引用：只提供标识。 */
    private Order eventRef(Long orderId) {
        Order order = mock(Order.class);
        when(order.getEntityId()).thenReturn(orderId);
        return order;
    }
}
