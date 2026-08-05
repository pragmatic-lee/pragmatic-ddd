package io.pragmatic.ddd.acl.fixture.domain;

/**
 * 支付预授权入参。
 *
 * @param traceNo 流水号（唯一键，用于幂等）
 * @param orderId 订单号
 * @param amount  金额
 * @author wizard-lee
 */
public record PaymentPreAuthParam(String traceNo, String orderId, Long amount) {
}
