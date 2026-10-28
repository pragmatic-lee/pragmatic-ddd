package io.pragmatic.ddd.example.order.application.order.service;

import io.pragmatic.ddd.example.order.domain.order.event.OrderDataSyncEvent;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica.OrderRedisSource;
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
 * 订单 Redis 缓存副本订阅测试：验证同步动作分流（PURGE 直清、UPSERT 聚合缺失转清、UPSERT 正常同步）。
 *
 * @author wizard-lee
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderRedisCacheHandle")
class OrderRedisCacheHandleTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderRedisSource redisSource;

    private OrderRedisCacheHandle handle;

    @BeforeEach
    void setUp() {
        this.handle = new OrderRedisCacheHandle(this.orderRepository, this.redisSource);
    }

    @Test
    void purgeAction_purgesReplicaWithoutLoadingAggregate() {
        Order orderRef = this.eventRef(11L);

        this.handle.handleEvent(OrderDataSyncEvent.purge(orderRef));

        verify(this.redisSource).purge(11L);
        verify(this.redisSource, never()).sync(any());
        verifyNoInteractions(this.orderRepository);
    }

    @Test
    void upsertAction_aggregateMissing_purgesReplica() {
        Order orderRef = this.eventRef(12L);
        when(this.orderRepository.findById(12L)).thenReturn(null);

        this.handle.handleEvent(OrderDataSyncEvent.upsert(orderRef));

        verify(this.redisSource).purge(12L);
        verify(this.redisSource, never()).sync(any());
    }

    @Test
    void upsertAction_aggregatePresent_syncsReplica() {
        Order orderRef = this.eventRef(13L);
        Order loaded = mock(Order.class);
        when(this.orderRepository.findById(13L)).thenReturn(loaded);

        this.handle.handleEvent(OrderDataSyncEvent.upsert(orderRef));

        verify(this.redisSource).sync(loaded);
        verify(this.redisSource, never()).purge(any());
    }

    /** 仅用于事件构造的聚合引用：只提供标识。 */
    private Order eventRef(Long orderId) {
        Order order = mock(Order.class);
        when(order.getEntityId()).thenReturn(orderId);
        return order;
    }
}
