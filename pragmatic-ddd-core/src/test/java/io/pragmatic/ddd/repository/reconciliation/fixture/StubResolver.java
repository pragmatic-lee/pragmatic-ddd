package io.pragmatic.ddd.repository.reconciliation.fixture;

import io.pragmatic.ddd.repository.reconciliation.IReadModelVersionResolver;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 版本解析器桩：返回预设的读模型版本 V'。
 */
public final class StubResolver implements IReadModelVersionResolver<Long> {

    private final ReconciliationTarget target;

    private final long readVersion;

    public StubResolver(ReconciliationTarget target, long readVersion) {
        this.target = target;
        this.readVersion = readVersion;
    }

    @Override
    public long resolve(Long aggregateId) {
        return readVersion;
    }

    @Override
    public ReconciliationTarget supportedTarget() {
        return target;
    }
}
