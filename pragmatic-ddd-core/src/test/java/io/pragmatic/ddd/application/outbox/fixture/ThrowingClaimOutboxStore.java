package io.pragmatic.ddd.application.outbox.fixture;

import io.pragmatic.ddd.application.outbox.OutboxMessage;

import java.time.Duration;
import java.util.List;

/**
 * 认领即抛异常的 Outbox 存储测试夹具：用于验证 Relay 对 claimPending 失败的隔离与日志。
 *
 * @author wizard-lee
 */
public class ThrowingClaimOutboxStore extends InMemoryOutboxStore {

    @Override
    public List<OutboxMessage> claimPending(int batchSize, Duration grace) {
        throw new IllegalStateException("claimPending failed");
    }
}
