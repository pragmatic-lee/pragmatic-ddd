package io.pragmatic.ddd.example.order.infrastructure.dependency.order;

/**
 * 积分响应打桩对象：承载业务幂等键与处理结果。
 *
 * @author wizard-lee
 */
public record PointsResponse(String bizId, boolean success) {
}
