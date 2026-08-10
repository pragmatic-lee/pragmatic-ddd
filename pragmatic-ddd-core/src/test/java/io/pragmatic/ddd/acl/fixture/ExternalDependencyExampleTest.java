package io.pragmatic.ddd.acl.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.pragmatic.ddd.acl.ExternalCallLogger;
import io.pragmatic.ddd.acl.fixture.adapter.PaymentExternalClient;
import io.pragmatic.ddd.acl.fixture.adapter.PaymentPreAuthGateway;
import io.pragmatic.ddd.acl.fixture.adapter.UserExternalClient;
import io.pragmatic.ddd.acl.fixture.adapter.UserGatewayAdapter;
import io.pragmatic.ddd.dependency.fixture.domain.IUserDependency;
import io.pragmatic.ddd.dependency.fixture.domain.IPaymentGatewayDependency;
import io.pragmatic.ddd.dependency.fixture.domain.PaymentPreAuthParam;
import io.pragmatic.ddd.dependency.fixture.domain.PaymentPreAuthResult;
import io.pragmatic.ddd.dependency.fixture.domain.UserLevel;


/**
 * 阶段三验证：业务侧如何使用声明式依赖 + 防腐适配器（组合式 / 继承式）。
 *
 * @author wizard-lee
 */
class ExternalDependencyExampleTest {

    @Test
    void userDependencyShouldQueryViaCompositionAdapter() {
        IUserDependency userDependency = new UserGatewayAdapter(new UserExternalClient());
        assertThat(userDependency.existsByUserId("u1")).isTrue();
        assertThat(userDependency.existsByUserId("unknown")).isFalse();
        assertThat(userDependency.getUserLevel("u1")).isEqualTo(new UserLevel(3));
    }

    @Test
    void userDependencyShouldLogViaSlf4jHook() {
        Logger log = LoggerFactory.getLogger("acl-fixture");
        ExternalCallLogger<UserExternalClient.UserLevelReq, UserExternalClient.UserLevelResp> logger =
                new ExternalCallLogger<>() {
                    @Override
                    public void onRequest(UserExternalClient.UserLevelReq request) {
                        log.info("[ACL][User] request={}", request);
                    }

                    @Override
                    public void onResponse(UserExternalClient.UserLevelResp response) {
                        log.info("[ACL][User] response={}", response);
                    }

                    @Override
                    public void onError(Throwable ex) {
                        log.error("[ACL][User] error", ex);
                    }
                };
        UserGatewayAdapter adapter = new UserGatewayAdapter(new UserExternalClient());
        adapter.setLogger(logger);
        assertThat(adapter.existsByUserId("u1")).isTrue();
    }

    @Test
    void paymentDependencyShouldWriteWhenNotExisting() {
        IPaymentGatewayDependency payment = new PaymentPreAuthGateway(new PaymentExternalClient());
        PaymentPreAuthResult result = payment.preAuth(new PaymentPreAuthParam("t-1", "o-1", 100L));
        assertThat(result.success()).isTrue();
    }

    @Test
    void paymentDependencyShouldShortCircuitOnIdempotentReplay() {
        PaymentExternalClient client = new PaymentExternalClient();
        PaymentPreAuthGateway gateway = new PaymentPreAuthGateway(client);
        PaymentPreAuthParam param = new PaymentPreAuthParam("t-2", "o-2", 200L);
        assertThat(gateway.preAuth(param).success()).isTrue();
        // 重放同一流水号 idempotent write 应短路，不重复调用支付
        PaymentPreAuthResult second = gateway.preAuth(param);
        assertThat(second.success()).isTrue();
        assertThat(client.query("t-2")).isNotNull();
    }
}
