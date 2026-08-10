package io.pragmatic.ddd.dependency.fixture.domain;

/**
 * 支付预授权结果。
 *
 * @param traceNo 流水号
 * @param success 是否成功
 * @author wizard-lee
 */
public record PaymentPreAuthResult(String traceNo, boolean success) {
}
