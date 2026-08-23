package io.pragmatic.ddd.example.order.domain.order.dependency;

import io.pragmatic.ddd.dependency.DependencyType;
import io.pragmatic.ddd.dependency.ExternalDependency;
import io.pragmatic.ddd.dependency.IDependency;

/**
 * 订单聚合对用户聚合的外部依赖声明：本聚合的金额折扣取决于用户等级。
 * 仅描述"依赖了什么 / 取什么"，不感知远程调用与转换细节（依赖倒置）。
 * 用户聚合当前未实现，按"先定义后实现"处理；运行期不强制校验。
 */
@ExternalDependency(
        targetName = "User",
        type = DependencyType.AGGREGATE,
        description = "用户聚合：提供用户等级以决定订单金额折扣"
)
public interface IUserDependency extends IDependency {

    /** 根据用户标识返回用户等级（整数）。 */
    int getUserLevel(String userId);
}
