package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

import java.util.Objects;

/**
 * 默认内存补偿编排实现（工厂）。
 * 持有可选的持久化补偿日志与默认补偿选项；未装配日志却以 durable 选项开启范围时显式失败。
 *
 * @author wizard-lee
 */
public final class LocalCompensationManager implements ICompensationManager {

    private final ICompensationLog log;
    private final CompensationOptions defaultOptions;

    /** 内存模式构造器（无持久化日志）。 */
    public LocalCompensationManager() {
        this(null, CompensationOptions.inMemory());
    }

    /**
     * 可装配持久化日志的构造器（默认仍为内存模式，保持既有语义）。
     *
     * @param log 持久化补偿日志，为 null 时等价于内存模式
     */
    public LocalCompensationManager(ICompensationLog log) {
        this(log, CompensationOptions.inMemory());
    }

    /**
     * 以指定默认选项构造：L3 接线入口，begin() 无参版即采用该选项。
     *
     * @param log            持久化补偿日志，为 null 时等价于内存模式
     * @param defaultOptions 默认补偿选项
     */
    public LocalCompensationManager(ICompensationLog log, CompensationOptions defaultOptions) {
        this.log = log;
        this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions");
    }

    @Override
    public ICompensationScope begin() {
        return begin(this.defaultOptions);
    }

    @Override
    public ICompensationScope begin(CompensationOptions options) {
        if (options.durable() && this.log == null) {
            throw new IllegalStateException("durable option requires ICompensationLog to be assembled");
        }
        return new LocalCompensationScope(this.log, options);
    }
}
