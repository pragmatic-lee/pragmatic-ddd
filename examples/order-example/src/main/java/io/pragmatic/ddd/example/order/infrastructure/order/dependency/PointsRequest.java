package io.pragmatic.ddd.example.order.infrastructure.order.dependency;

/**
 * 积分请求打桩对象：承载用户标识、积分数量与业务幂等键。
 *
 * @author wizard-lee
 */
public record PointsRequest(Long customerId, int points, String bizId) {
}
