package io.pragmatic.ddd.acl.fixture.adapter;

import io.pragmatic.ddd.acl.ExternalCall;
import io.pragmatic.ddd.acl.ExternalCallLogger;
import io.pragmatic.ddd.acl.fixture.domain.IUserDependency;
import io.pragmatic.ddd.acl.fixture.domain.UserLevel;

/**
 * 防腐层适配器：组合式（ExternalCall）实现 User 查询依赖。
 * 把领域契约与对方接口隔离开：入参 / 返回值在领域侧转换，远程调用在对方 Client 内。
 *
 * @author wizard-lee
 */
public class UserGatewayAdapter implements IUserDependency {

    private final UserExternalClient client;

    /** 日志钩子，默认空实现；业务侧可替换为 SLF4J 实现。 */
    private ExternalCallLogger<UserExternalClient.UserLevelReq, UserExternalClient.UserLevelResp> logger =
            ExternalCallLogger.noop();

    public UserGatewayAdapter(UserExternalClient client) {
        this.client = client;
    }

    public void setLogger(ExternalCallLogger<UserExternalClient.UserLevelReq,
            UserExternalClient.UserLevelResp> logger) {
        this.logger = logger;
    }

    @Override
    public boolean existsByUserId(String userId) {
        return ExternalCall.query(
                userId,
                UserExternalClient.UserExistsReq::new,
                req -> new UserExternalClient.UserExistsResp(client.exists(req.userId())),
                UserExternalClient.UserExistsResp::exist,
                ExternalCallLogger.<UserExternalClient.UserExistsReq, UserExternalClient.UserExistsResp>noop());
    }

    @Override
    public UserLevel getUserLevel(String userId) {
        return ExternalCall.query(
                userId,
                UserExternalClient.UserLevelReq::new,
                req -> new UserExternalClient.UserLevelResp(client.level(req.userId())),
                resp -> new UserLevel(resp.level()),
                logger);
    }
}
