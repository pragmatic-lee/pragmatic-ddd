package io.pragmatic.ddd.application.compensation;

/**
 * 补偿编排选项。
 *
 * @param durable       是否开启持久化补偿日志（WAL + 中继兜底），默认关闭
 * @param defaultPolicy 默认补偿重试策略
 * @author wizard-lee
 */
public record CompensationOptions(boolean durable, CompensationPolicy defaultPolicy) {

    /** 内存模式（默认）：不落持久化日志，DB 零依赖。 */
    public static CompensationOptions inMemory() {
        return new CompensationOptions(false, CompensationPolicy.defaultPolicy());
    }

    /**
     * 持久化模式：开启 WAL 与中继兜底，需装配 ICompensationLog。
     * 方法名相对设计文档的 durable() 做区分——record 的 durable() 组件已占用该签名。
     */
    public static CompensationOptions durableMode() {
        return new CompensationOptions(true, CompensationPolicy.defaultPolicy());
    }
}
