package io.pragmatic.ddd.application.compensation;

/**
 * 单个补偿失败明细。
 *
 * @param actionKey 幂等键
 * @param cause     失败原因
 * @author wizard-lee
 */
public record CompensationFailure(String actionKey, Throwable cause) {
}
