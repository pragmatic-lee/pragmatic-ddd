package io.pragmatic.ddd.example.order.domain.order.model.valueobject;

/**
 * 增加积分命令值对象：承载用户标识、积分数量与业务幂等键。
 *
 * @param customerId 用户标识。
 * @param points     增加积分数。
 * @param bizId      业务幂等键，本特性取订单号，用于防止重复发放。
 * @author wizard-lee
 */
public record IncreasePointsCommand(Long customerId, int points, String bizId) {

}
