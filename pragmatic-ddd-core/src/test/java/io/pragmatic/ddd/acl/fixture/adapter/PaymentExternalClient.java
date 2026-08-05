package io.pragmatic.ddd.acl.fixture.adapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟的对方支付系统客户端（仅存在于防腐层）。生产环境替换为真实 RPC / HTTP 客户端。
 *
 * @author wizard-lee
 */
public class PaymentExternalClient {

    private final Map<String, PreAuthResp> store = new ConcurrentHashMap<>();

    public PreAuthResp query(String traceNo) {
        return store.get(traceNo);
    }

    public PreAuthResp preAuth(PreAuthReq req) {
        PreAuthResp resp = new PreAuthResp(req.traceNo(), true);
        store.put(req.traceNo(), resp);
        return resp;
    }

    public record PreAuthReq(String traceNo, String orderId, Long amount) {
    }

    public record PreAuthResp(String traceNo, boolean success) {
    }
}
