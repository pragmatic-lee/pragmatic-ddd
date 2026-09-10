package io.pragmatic.ddd.repository.reconciliation;

/**
 * 扫描器参数载体。
 *
 * @author wizard-lee
 */
public class ScanConfig {

    private final long initialDelaySeconds;
    private final long intervalSeconds;
    private final int batchSize;
    private final int concurrency;

    public ScanConfig(long initialDelaySeconds, long intervalSeconds, int batchSize, int concurrency) {
        this.initialDelaySeconds = initialDelaySeconds;
        this.intervalSeconds = intervalSeconds;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
    }

    public long initialDelaySeconds() {
        return initialDelaySeconds;
    }

    public long intervalSeconds() {
        return intervalSeconds;
    }

    public int batchSize() {
        return batchSize;
    }

    public int concurrency() {
        return concurrency;
    }
}
