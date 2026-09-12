package io.pragmatic.ddd.repository.reconciliation;

import lombok.Getter;

/**
 * 扫描器参数载体。
 *
 * @author wizard-lee
 */
@Getter
public class ScanConfig {

    private final long initialDelaySeconds;
    private final long intervalSeconds;
    private final int batchSize;
    private final int concurrency;

    public ScanConfig(long initialDelaySeconds,
                      long intervalSeconds,
                      int batchSize,
                      int concurrency) {
        this.initialDelaySeconds = initialDelaySeconds;
        this.intervalSeconds = intervalSeconds;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
    }
}
