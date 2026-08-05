package io.pragmatic.ddd.acl.fixture.domain;

import io.pragmatic.ddd.acl.DependencyType;
import io.pragmatic.ddd.acl.ExternalDependency;
import io.pragmatic.ddd.acl.IDependency;

/**
 * 领域层声明：本聚合依赖支付系统（先查后写，按流水号幂等）。
 *
 * @author wizard-lee
 */
@ExternalDependency(targetName = "PaymentGateway", type = DependencyType.EXTERNAL_SYSTEM,
        description = "支付预授权，按流水号幂等")
public interface IPaymentGatewayDependency extends IDependency {

    PaymentPreAuthResult preAuth(PaymentPreAuthParam param);
}
