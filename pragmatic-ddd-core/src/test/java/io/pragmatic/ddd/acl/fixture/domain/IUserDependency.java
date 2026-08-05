package io.pragmatic.ddd.acl.fixture.domain;

import io.pragmatic.ddd.acl.DependencyType;
import io.pragmatic.ddd.acl.ExternalDependency;
import io.pragmatic.ddd.acl.IDependency;

/**
 * 领域层声明：本聚合依赖 User 聚合（查询）。
 * 仅描述契约，不感知远程调用与转换细节（依赖倒置）。
 *
 * @author wizard-lee
 */
@ExternalDependency(targetName = "User", type = DependencyType.AGGREGATE,
        description = "查询用户信息，用于规则校验")
public interface IUserDependency extends IDependency {

    boolean existsByUserId(String userId);

    UserLevel getUserLevel(String userId);
}
