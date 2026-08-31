package io.pragmatic.ddd.example.order.infrastructure.order.dependency;

import io.pragmatic.ddd.example.order.domain.order.dependency.IUserDependency;
import org.springframework.stereotype.Component;

/**
 * 用户依赖防腐适配器：实现领域层 IUserDependency，对外获取用户等级与手机号。
 * 用户聚合当前未实现，此处先以打桩返回默认等级（0=普通）与默认手机号，运行期不强制校验；
 * 用户聚合就绪后改为真实 RPC / DAO 实现即可。
 */
@Component
public class UserGatewayAdapter implements IUserDependency {

    @Override
    public int getUserLevel(String userId) {
        return 0;
    }

    @Override
    public String getUserMobile(String userId) {
        // 打桩：真实场景查询用户聚合 / 用户服务
        return "13800000000";
    }
}
