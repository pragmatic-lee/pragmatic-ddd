package io.pragmatic.ddd.application.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxMessage 数据容器测试：确认默认状态、getter/setter 读写与全字段可观测。
  * @author wizard-lee
 */
class OutboxMessageTest {

    @Test
    void newMessage_hasNullsByDefault() {
        OutboxMessage m = new OutboxMessage();
        assertThat(m.getId()).isNull();
        assertThat(m.getStatus()).isNull();
        assertThat(m.getAttempts()).isZero();
        assertThat(m.getQueue()).isZero();
    }

    @Test
    void setters_populateAllObservabilityFields() {
        Instant now = Instant.now();
        OutboxMessage m = new OutboxMessage();
        m.setId("id-1");
        m.setAggregateId("agg-1");
        m.setAggregateType("com.example.Order");
        m.setEventType("com.example.OrderCreated");
        m.setEntityId("e-1");
        m.setPayload("{}");
        m.setStatus(OutboxStatus.PENDING);
        m.setAttempts(3);
        m.setQueue(0);
        m.setCreatedAt(now);
        m.setClaimedAt(now);
        m.setSentAt(now);
        m.setLastError("boom");

        assertThat(m.getId()).isEqualTo("id-1");
        assertThat(m.getAggregateId()).isEqualTo("agg-1");
        assertThat(m.getAggregateType()).isEqualTo("com.example.Order");
        assertThat(m.getEventType()).isEqualTo("com.example.OrderCreated");
        assertThat(m.getEntityId()).isEqualTo("e-1");
        assertThat(m.getPayload()).isEqualTo("{}");
        assertThat(m.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(m.getAttempts()).isEqualTo(3);
        assertThat(m.getQueue()).isZero();
        assertThat(m.getCreatedAt()).isEqualTo(now);
        assertThat(m.getClaimedAt()).isEqualTo(now);
        assertThat(m.getSentAt()).isEqualTo(now);
        assertThat(m.getLastError()).isEqualTo("boom");
    }
}
