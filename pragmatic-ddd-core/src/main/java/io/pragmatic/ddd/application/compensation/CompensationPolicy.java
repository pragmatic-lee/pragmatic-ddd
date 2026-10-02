package io.pragmatic.ddd.application.compensation;

import java.time.Duration;

/**
 * 补偿重试策略。
 *
 * @param maxAttempts 单次补偿的最大尝试次数（含首次）
 * @param backoff     相邻两次尝试的间隔
 * @author wizard-lee
 */
public record CompensationPolicy(int maxAttempts, Duration backoff) {

    /** 默认策略：3 次尝试，间隔 200ms（L2 阻塞在请求线程，必须很短）。 */
    public static CompensationPolicy defaultPolicy() {
        return new CompensationPolicy(3, Duration.ofMillis(200));
    }
}
