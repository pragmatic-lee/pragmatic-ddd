package io.pragmatic.ddd.example.order.domain.order.dependency;

import io.pragmatic.ddd.dependency.DependencyType;
import io.pragmatic.ddd.dependency.ExternalDependency;
import io.pragmatic.ddd.dependency.IDependency;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.IncreasePointsCommand;

/**
 * 订单聚合对积分服务的外部依赖声明：按业务幂等键为指定用户增加积分。
 * 仅声明"加积分"这一能力，不感知积分服务实现细节（依赖倒置）。
 *
 * @author wizard-lee
 */
@ExternalDependency(
        targetName = "PointsService",
        type = DependencyType.EXTERNAL_SYSTEM,
        description = "积分服务：按业务幂等键为指定用户增加积分")
public interface IUserPointsDependency extends IDependency {

    /** 按命令为指定用户增加积分，bizId 为业务幂等键。 */
    void increasePoints(IncreasePointsCommand command);
}
