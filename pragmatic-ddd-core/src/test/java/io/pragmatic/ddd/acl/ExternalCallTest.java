package io.pragmatic.ddd.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * 阶段二验证：ExternalCall 组合式调用器的查询 / 写入 / 先查后写行为。
 *
 * @author wizard-lee
 */
class ExternalCallTest {

    @Test
    void queryShouldConvertAndCallAndConvertBack() {
        String result = ExternalCall.query(
                "u1",
                userId -> "req:" + userId,
                req -> "resp:" + req,
                resp -> "domain:" + resp);
        assertThat(result).isEqualTo("domain:resp:req:u1");
    }

    @Test
    void queryShouldFireLoggerHooks() {
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
        ExternalCall.query("u1",
                userId -> "req:" + userId,
                req -> "resp:" + req,
                resp -> "domain:" + resp,
                logger);
        assertThat(events).containsExactly("request:req:u1", "response:resp:req:u1");
    }

    @Test
    void queryShouldRethrowAndLogError() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        ExternalCallLogger<String, String> logger = new ExternalCallLogger<>() {
            @Override
            public void onError(Throwable ex) {
                captured.set(ex);
            }
        };
        RuntimeException ex = new RuntimeException("boom");
        assertThatThrownBy(() -> ExternalCall.query("u1",
                Function.identity(),
                req -> {
                    throw ex;
                },
                Function.identity(),
                logger)).isSameAs(ex);
        assertThat(captured).hasValue(ex);
    }

    @Test
    void writeShouldConvertAndCallAndConvertBack() {
        String result = ExternalCall.write(
                "order1",
                o -> "req:" + o,
                req -> "resp:" + req,
                resp -> "domain:" + resp);
        assertThat(result).isEqualTo("domain:resp:req:order1");
    }

    @Test
    void writeIdempotentShouldCallDoWriteWhenNotExisting() {
        AtomicBoolean called = new AtomicBoolean(false);
        String result = ExternalCall.writeIdempotent(
                "trace-1",
                Function.identity(),
                key -> Optional.empty(),
                existing -> "should-not-reach",
                key -> "req:" + key,
                req -> {
                    called.set(true);
                    return "resp:" + req;
                },
                resp -> "domain:" + resp);
        assertThat(called).isTrue();
        assertThat(result).isEqualTo("domain:resp:req:trace-1");
    }

    @Test
    void writeIdempotentShouldShortCircuitWhenExisting() {
        AtomicBoolean called = new AtomicBoolean(false);
        String result = ExternalCall.writeIdempotent(
                "trace-1",
                Function.identity(),
                key -> Optional.of("existing-resp"),
                existing -> "domain-existing:" + existing,
                key -> "req:" + key,
                req -> {
                    called.set(true);
                    return "resp:" + req;
                },
                resp -> "domain:" + resp);
        assertThat(called).isFalse();
        assertThat(result).isEqualTo("domain-existing:existing-resp");
    }

    @Test
    void writeIdempotentShouldLogResponseOnShortCircuit() {
        List<String> events = new ArrayList<>();
        ExternalCallLogger<String, String> logger = new ExternalCallLogger<>() {
            @Override
            public void onResponse(String response) {
                events.add("response:" + response);
            }
        };
        ExternalCall.writeIdempotent(
                "trace-1",
                Function.identity(),
                key -> Optional.of("existing-resp"),
                existing -> "domain-existing:" + existing,
                key -> "req:" + key,
                req -> "resp:" + req,
                resp -> "domain:" + resp,
                logger);
        assertThat(events).containsExactly("response:existing-resp");
    }
}
