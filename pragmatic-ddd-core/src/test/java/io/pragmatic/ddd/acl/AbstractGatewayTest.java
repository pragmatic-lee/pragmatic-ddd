package io.pragmatic.ddd.acl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/**
 * 阶段二验证：继承式 Abstract*Gateway 的查询 / 写入 / 先查后写行为。
 *
 * @author wizard-lee
 */
class AbstractGatewayTest {

    static final class QueryProbe extends AbstractQueryGateway<String, String, String, String> {
        @Override
        protected String toExternalRequest(String param) {
            return "req:" + param;
        }

        @Override
        protected String doQuery(String request) {
            return "resp:" + request;
        }

        @Override
        protected String toDomainResult(String response) {
            return "domain:" + response;
        }
    }

    static final class WriteProbe extends AbstractWriteGateway<String, String, String, String> {
        final AtomicBoolean called = new AtomicBoolean(false);

        @Override
        protected String toExternalRequest(String param) {
            return "req:" + param;
        }

        @Override
        protected String doWrite(String request) {
            called.set(true);
            return "resp:" + request;
        }

        @Override
        protected String toDomainResult(String response) {
            return "domain:" + response;
        }
    }

    static class IdempotentProbe extends AbstractIdempotentWriteGateway<String, String, String, String, String> {
        final AtomicBoolean writeCalled = new AtomicBoolean(false);
        final AtomicBoolean queryCalled = new AtomicBoolean(false);

        @Override
        protected String uniqueKey(String param) {
            return param;
        }

        @Override
        protected Optional<String> queryByKey(String key) {
            queryCalled.set(true);
            return Optional.empty();
        }

        @Override
        protected String toDomainResultFromExisting(String existing) {
            return "domain-existing:" + existing;
        }

        @Override
        protected String toExternalRequest(String param) {
            return "req:" + param;
        }

        @Override
        protected String doWrite(String request) {
            writeCalled.set(true);
            return "resp:" + request;
        }

        @Override
        protected String toDomainResult(String response) {
            return "domain:" + response;
        }
    }

    @Test
    void queryGatewayShouldRunTemplate() {
        assertThat(new QueryProbe().query("u1")).isEqualTo("domain:resp:req:u1");
    }

    @Test
    void writeGatewayShouldRunTemplate() {
        WriteProbe probe = new WriteProbe();
        assertThat(probe.write("o1")).isEqualTo("domain:resp:req:o1");
        assertThat(probe.called).isTrue();
    }

    @Test
    void idempotentGatewayShouldCallWriteWhenNotExisting() {
        IdempotentProbe probe = new IdempotentProbe();
        assertThat(probe.write("t1")).isEqualTo("domain:resp:req:t1");
        assertThat(probe.queryCalled).isTrue();
        assertThat(probe.writeCalled).isTrue();
    }

    @Test
    void idempotentGatewayShouldShortCircuitWhenExisting() {
        IdempotentProbe probe = new IdempotentProbe() {
            @Override
            protected Optional<String> queryByKey(String key) {
                return Optional.of("existing-resp");
            }
        };
        assertThat(probe.write("t1")).isEqualTo("domain-existing:existing-resp");
        assertThat(probe.writeCalled).isFalse();
    }

    @Test
    void gatewayShouldInvokeInjectedLogger() {
        List<String> events = new ArrayList<>();
        ExternalCallLogger<String, String> logger = new ExternalCallLogger<>() {
            @Override
            public void onRequest(String request) {
                events.add("request:" + request);
            }

            @Override
            public void onResponse(String response) {
                events.add("response:" + response);
            }
        };
        QueryProbe probe = new QueryProbe();
        probe.setLogger(logger);
        probe.query("u1");
        assertThat(events).containsExactly("request:req:u1", "response:resp:req:u1");
    }
}
