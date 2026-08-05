package io.pragmatic.ddd.application.outbox.fixture;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.application.outbox.OutboxStatus;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内存版 IOutboxStore 测试夹具：用 Map 模拟存储，并记录 store/markSent/release/markFailed 等调用，
 * 便于 outbox 各组件单元测试做确定性断言。
 *
 * @author wizard-lee
 */
public class InMemoryOutboxStore implements IOutboxStore {

    private final Map<String, OutboxMessage> rows = new LinkedHashMap<>();
    private int storeCount = 0;
    private final List<String> markSentIds = new ArrayList<>();
    private final List<String> releaseIds = new ArrayList<>();
    private final List<String> markFailedIds = new ArrayList<>();

    @Override
    public void store(List<OutboxMessage> messages) {
        storeCount++;
        for (OutboxMessage m : messages) {
            rows.put(m.getId(), copy(m));
        }
    }

    @Override
    public OutboxMessage claim(String id) {
        OutboxMessage m = rows.get(id);
        if (m != null && m.getStatus() == OutboxStatus.PENDING) {
            m.setStatus(OutboxStatus.PROCESSING);
            m.setClaimedAt(Instant.now());
        }
        return m != null ? copy(m) : null;
    }

    @Override
    public void markSent(String id) {
        OutboxMessage m = rows.get(id);
        // 带守卫：仅 PENDING/PROCESSING → SENT，已 FAILED 的行不可被覆盖（幂等）
        if (m != null && (m.getStatus() == OutboxStatus.PENDING || m.getStatus() == OutboxStatus.PROCESSING)) {
            m.setStatus(OutboxStatus.SENT);
            m.setSentAt(Instant.now());
        }
        markSentIds.add(id);
    }

    @Override
    public void release(String id) {
        OutboxMessage m = rows.get(id);
        if (m != null && m.getStatus() == OutboxStatus.PROCESSING) {
            m.setStatus(OutboxStatus.PENDING);
        }
        releaseIds.add(id);
    }

    @Override
    public List<OutboxMessage> claimPending(int batchSize, Duration grace) {
        List<OutboxMessage> claimed = new ArrayList<>();
        Instant cutoff = Instant.now().minus(grace);
        for (OutboxMessage m : rows.values()) {
            if (claimed.size() >= batchSize) {
                break;
            }
            if (m.getStatus() == OutboxStatus.PENDING
                    && (m.getCreatedAt() == null || m.getCreatedAt().isBefore(cutoff))) {
                m.setStatus(OutboxStatus.PROCESSING);
                m.setClaimedAt(Instant.now());
                claimed.add(copy(m));
            }
        }
        return claimed;
    }

    @Override
    public int incrementAttempts(String id) {
        OutboxMessage m = rows.get(id);
        if (m == null) {
            return 0;
        }
        m.setAttempts(m.getAttempts() + 1);
        return m.getAttempts();
    }

    @Override
    public void markFailed(String id) {
        OutboxMessage m = rows.get(id);
        if (m != null) {
            m.setStatus(OutboxStatus.FAILED);
        }
        markFailedIds.add(id);
    }

    /** 返回某条消息的副本，不存在返回 null。 */
    public OutboxMessage find(String id) {
        OutboxMessage m = rows.get(id);
        return m != null ? copy(m) : null;
    }

    /** 返回全部消息的副本列表。 */
    public List<OutboxMessage> all() {
        List<OutboxMessage> result = new ArrayList<>();
        for (OutboxMessage m : rows.values()) {
            result.add(copy(m));
        }
        return result;
    }

    public int storeCount() {
        return storeCount;
    }

    public List<String> markSentIds() {
        return markSentIds;
    }

    public List<String> releaseIds() {
        return releaseIds;
    }

    public List<String> markFailedIds() {
        return markFailedIds;
    }

    public int size() {
        return rows.size();
    }

    private static OutboxMessage copy(OutboxMessage m) {
        OutboxMessage c = new OutboxMessage();
        c.setId(m.getId());
        c.setAggregateId(m.getAggregateId());
        c.setAggregateType(m.getAggregateType());
        c.setEventType(m.getEventType());
        c.setEntityId(m.getEntityId());
        c.setPayload(m.getPayload());
        c.setStatus(m.getStatus());
        c.setAttempts(m.getAttempts());
        c.setQueue(m.getQueue());
        c.setCreatedAt(m.getCreatedAt());
        c.setClaimedAt(m.getClaimedAt());
        c.setSentAt(m.getSentAt());
        c.setLastError(m.getLastError());
        return c;
    }
}
