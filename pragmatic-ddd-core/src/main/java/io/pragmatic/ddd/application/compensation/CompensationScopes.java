package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

/**
 * 补偿范围工厂（框架内部）：以内聚方式创建补偿范围，并保证 durable 与日志装配的一致性。
 *
 * @author wizard-lee
 */
final class CompensationScopes {

    private CompensationScopes() {
    }

    /**
     * 创建一个补偿范围。
     *
     * @param compensationLog 持久化补偿日志，内存模式下为 null
     * @param options         补偿选项
     * @return 补偿范围
     */
    static LocalCompensationScope begin(ICompensationLog compensationLog, CompensationOptions options) {
        if (options.durable() && compensationLog == null) {
            throw new IllegalStateException("durable option requires ICompensationLog to be assembled");
        }
        return new LocalCompensationScope(compensationLog, options);
    }
}
