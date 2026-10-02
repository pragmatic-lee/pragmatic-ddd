package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

/**
 * 默认内存补偿编排实现（工厂）。
 * 持有可选的持久化补偿日志；未装配日志却以 durable 选项开启范围时显式失败。
 *
 * @author wizard-lee
 */
public final class LocalCompensationManager implements ICompensationManager {

    private final ICompensationLog log;

    /** 内存模式构造器（无持久化日志）。 */
    public LocalCompensationManager() {
        this(null);
    }

    /**
     * 可装配持久化日志的构造器。
     *
     * @param log 持久化补偿日志，为 null 时等价于内存模式
     */
    public LocalCompensationManager(ICompensationLog log) {
        this.log = log;
    }

    @Override
    public ICompensationScope begin() {
        return begin(CompensationOptions.inMemory());
    }

    @Override
    public ICompensationScope begin(CompensationOptions options) {
        if (options.durable() && log == null) {
            throw new IllegalStateException("durable option requires ICompensationLog to be assembled");
        }
        return new LocalCompensationScope(log, options);
    }
}
