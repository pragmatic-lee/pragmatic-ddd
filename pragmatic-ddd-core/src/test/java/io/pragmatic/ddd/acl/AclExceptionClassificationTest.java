package io.pragmatic.ddd.acl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * ACL 调用器异常分类验证：转换失败归为 AclConversionException（不可重试），
 * 通信失败归为 AclCommunicationException（通常可重试），并校验根因保留与 onError 钩子语义。
 *
 * @author wizard-lee
 */
class AclExceptionClassificationTest {

    private static ExternalCallLogger<String, String> errorCapturingLogger(AtomicReference<Throwable> captured) {
        return new ExternalCallLogger<>() {
            @Override
            public void onError(Throwable ex) {
                captured.set(ex);
            }
        };
    }

    @Test
    void writeShouldClassifyCommunicationError() {
        AtomicReference<Throwable> captured = new AtomicReference<>();
        RuntimeException cause = new RuntimeException("network");
        assertThatThrownBy(() -> ExternalCall.write("o1",
                Function.identity(),
                req -> {
                    throw cause;
                },
                Function.identity(),
                errorCapturingLogger(captured)))
                .isInstanceOf(AclCommunicationException.class)
                .hasCause(cause);
        assertThat(captured).hasValue(cause);
    }

    @Test
    void writeShouldClassifyConversionErrorOnResponse() {
        RuntimeException cause = new RuntimeException("bad-result");
        assertThatThrownBy(() -> ExternalCall.write("o1",
                Function.identity(),
                Function.identity(),
                resp -> {
                    throw cause;
                }))
                .isInstanceOf(AclConversionException.class)
                .hasCause(cause);
    }

    @Test
    void writeIdempotentShouldClassifyUniqueKeyConversionError() {
        RuntimeException cause = new RuntimeException("bad-key");
        assertThatThrownBy(() -> ExternalCall.writeIdempotent("t1",
                param -> {
                    throw cause;
                },
                key -> Optional.empty(),
                existing -> "r",
                param -> "req",
                req -> "resp",
                resp -> "r"))
                .isInstanceOf(AclConversionException.class)
                .hasCause(cause);
    }

    @Test
    void writeIdempotentShouldClassifyQueryByKeyCommunicationError() {
        RuntimeException cause = new RuntimeException("query-failed");
        assertThatThrownBy(() -> ExternalCall.writeIdempotent("t1",
                Function.identity(),
                key -> {
                    throw cause;
                },
                existing -> "r",
                param -> "req",
                req -> "resp",
                resp -> "r"))
                .isInstanceOf(AclCommunicationException.class)
                .hasCause(cause);
    }

    @Test
    void writeIdempotentShouldClassifyExistingConversionErrorOnShortCircuit() {
        RuntimeException cause = new RuntimeException("bad-existing");
        assertThatThrownBy(() -> ExternalCall.writeIdempotent("t1",
                Function.identity(),
                key -> Optional.of("existing-resp"),
                existing -> {
                    throw cause;
                },
                param -> "req",
                req -> "resp",
                resp -> "r"))
                .isInstanceOf(AclConversionException.class)
                .hasCause(cause);
    }

    @Test
    void callShouldNotRewrapAclExceptionFromConversion() {
        AclConversionException original = new AclConversionException("已带语义");
        AclConversionException thrown = (AclConversionException) catchException(() -> ExternalCall.query("u1",
                param -> {
                    throw original;
                },
                Function.identity(),
                Function.identity()));
        assertThat(thrown).isSameAs(original);
    }

    @Test
    void callShouldNotRewrapAclExceptionFromCommunication() {
        AclCommunicationException original = new AclCommunicationException((Throwable) null);
        AclCommunicationException thrown = (AclCommunicationException) catchException(() -> ExternalCall.query("u1",
                Function.identity(),
                req -> {
                    throw original;
                },
                Function.identity()));
        assertThat(thrown).isSameAs(original);
    }

    @Test
    void idempotentGatewayShortCircuitConversionError() {
        AbstractIdempotentWriteGateway<String, String, String, String, String> probe =
                new AbstractIdempotentWriteGateway<>() {
                    @Override
                    protected String uniqueKey(String param) {
                        return param;
                    }

                    @Override
                    protected Optional<String> queryByKey(String key) {
                        return Optional.of("existing");
                    }

                    @Override
                    protected String toDomainResultFromExisting(String existing) {
                        throw new RuntimeException("bad-existing");
                    }

                    @Override
                    protected String toExternalRequest(String param) {
                        return param;
                    }

                    @Override
                    protected String doWrite(String request) {
                        return request;
                    }

                    @Override
                    protected String toDomainResult(String response) {
                        return response;
                    }
                };
        assertThatThrownBy(() -> probe.write("t1"))
                .isInstanceOf(AclConversionException.class);
    }

    private static Throwable catchException(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            return t;
        }
        throw new IllegalStateException("期望抛出异常但未抛出");
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
