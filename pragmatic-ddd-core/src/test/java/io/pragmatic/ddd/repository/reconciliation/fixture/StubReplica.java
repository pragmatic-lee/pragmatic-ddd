package io.pragmatic.ddd.repository.reconciliation.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IReadModelReplica;
import io.pragmatic.ddd.repository.ReplicaKey;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 可对账副本桩：返回预设的读模型版本 V'，并记录最近一次 rebuild / purgeOrphan 的聚合 id。
 * 仅用于对账编排单测，不承载写（materialize）能力。
 */
public final class StubReplica implements IReadModelReplica<Long> {

    /** 本桩默认关联的聚合类型。 */
    public static final Class<? extends AggregateRoot<Long>> DEFAULT_AGGREGATE_TYPE = StubAggregate.class;

    private final ReplicaKey key;

    private final long readVersion;

    /** 最近一次 rebuild 的聚合 id。 */
    public final AtomicReference<Long> lastRebuiltId = new AtomicReference<>();

    /** 最近一次 purgeOrphan 的聚合 id。 */
    public final AtomicReference<Long> lastPurgedId = new AtomicReference<>();

    public StubReplica(ReplicaKey key, long readVersion) {
        this.key = key;
        this.readVersion = readVersion;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends AggregateRoot<Long>> aggregateType() {
        return (Class<? extends AggregateRoot<Long>>) key.aggregateType();
    }

    @Override
    public String replicaId() {
        return key.replicaId();
    }

    @Override
    public long readVersion(Long aggregateId) {
        return readVersion;
    }

    @Override
    public void rebuild(Long aggregateId) {
        lastRebuiltId.set(aggregateId);
    }

    @Override
    public void purgeOrphan(Long aggregateId) {
        lastPurgedId.set(aggregateId);
    }
}
