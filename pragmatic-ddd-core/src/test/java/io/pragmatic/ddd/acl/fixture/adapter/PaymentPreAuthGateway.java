package io.pragmatic.ddd.acl.fixture.adapter;

import java.util.Optional;

import io.pragmatic.ddd.acl.AbstractIdempotentWriteGateway;
import io.pragmatic.ddd.acl.fixture.domain.IPaymentGatewayDependency;
import io.pragmatic.ddd.acl.fixture.domain.PaymentPreAuthParam;
import io.pragmatic.ddd.acl.fixture.domain.PaymentPreAuthResult;

/**
 * 防腐层适配器：继承式（AbstractIdempotentWriteGateway）实现支付预授权（先查后写，按流水号幂等）。
 *
 * @author wizard-lee
 */
public class PaymentPreAuthGateway extends AbstractIdempotentWriteGateway<PaymentPreAuthParam,
        PaymentPreAuthResult, PaymentExternalClient.PreAuthReq, PaymentExternalClient.PreAuthResp, String>
        implements IPaymentGatewayDependency {

    private final PaymentExternalClient client;

    public PaymentPreAuthGateway(PaymentExternalClient client) {
        this.client = client;
    }

    @Override
    protected String uniqueKey(PaymentPreAuthParam param) {
        return param.traceNo();
    }

    @Override
    protected Optional<PaymentExternalClient.PreAuthResp> queryByKey(String key) {
        return Optional.ofNullable(client.query(key));
    }

    @Override
    protected PaymentPreAuthResult toDomainResultFromExisting(PaymentExternalClient.PreAuthResp existing) {
        return new PaymentPreAuthResult(existing.traceNo(), existing.success());
    }

    @Override
    protected PaymentExternalClient.PreAuthReq toExternalRequest(PaymentPreAuthParam param) {
        return new PaymentExternalClient.PreAuthReq(param.traceNo(), param.orderId(), param.amount());
    }

    @Override
    protected PaymentExternalClient.PreAuthResp doWrite(PaymentExternalClient.PreAuthReq request) {
        return client.preAuth(request);
    }

    @Override
    protected PaymentPreAuthResult toDomainResult(PaymentExternalClient.PreAuthResp response) {
        return new PaymentPreAuthResult(response.traceNo(), response.success());
    }

    @Override
    public PaymentPreAuthResult preAuth(PaymentPreAuthParam param) {
        return write(param);
    }
}
