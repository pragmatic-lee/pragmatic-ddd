package io.pragmatic.ddd.application.compensation.relay;

import io.pragmatic.ddd.application.compensation.CompensationStatus;
import io.pragmatic.ddd.application.compensation.InMemoryCompensationLog;
import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 补偿中继测试：路由、悬挂不补偿、失败转死信、并发认领唯一。
 *
 * @author wizard-lee
 */
class CompensationRelayTest {

    @Test
    void pollOnce_routesByHandler_andMarksCompensated() {
        InMemoryCompensationLog log = new InMemoryCompensationLog();
        log.seed(record("k1", CompensationStatus.EXECUTED, "inventory", "RN-1"));
        List<String> handled = new CopyOnWriteArrayList<>();

        new CompensationRelay(log, List.of(handler("inventory", handled))).pollOnce();

        assertThat(handled).containsExactly("RN-1");
        assertThat(log.get("k1").status()).isEqualTo(CompensationStatus.COMPENSATED);
    }

    @Test
    void pollOnce_noHandler_marksFailed() {
        InMemoryCompensationLog log = new InMemoryCompensationLog();
        log.seed(record("k1", CompensationStatus.EXECUTED, "missing", "p"));

        new CompensationRelay(log, List.of()).pollOnce();

        assertThat(log.get("k1").status()).isEqualTo(CompensationStatus.FAILED);
    }

    @Test
    void pollOnce_suspendedRecord_isNotCompensated() {
        InMemoryCompensationLog log = new InMemoryCompensationLog();
        log.seed(record("s1", CompensationStatus.PENDING, "inventory", "p"));
        List<String> handled = new CopyOnWriteArrayList<>();

        new CompensationRelay(log, List.of(handler("inventory", handled))).pollOnce();

        assertThat(handled).isEmpty();
        assertThat(log.get("s1").status()).isEqualTo(CompensationStatus.PENDING);
    }

    @Test
    void pollOnce_compensationFails_marksFailedAndIncrementsAttempts() {
        InMemoryCompensationLog log = new InMemoryCompensationLog();
        log.seed(record("k1", CompensationStatus.EXECUTED, "inventory", "p"));
        ICompensationHandler failing = new ICompensationHandler() {
            @Override
            public String actionName() {
                return "inventory";
            }

            @Override
            public void compensate(String payload) {
                throw new IllegalStateException("nope");
            }
        };

        new CompensationRelay(log, List.of(failing)).pollOnce();

        assertThat(log.get("k1").status()).isEqualTo(CompensationStatus.FAILED);
        assertThat(log.get("k1").attempts()).isEqualTo(1);
    }

    @Test
    void concurrentPollOnce_sameRecord_isClaimedOnce() throws InterruptedException {
        InMemoryCompensationLog log = new InMemoryCompensationLog();
        log.seed(record("k1", CompensationStatus.EXECUTED, "inventory", "p"));
        List<String> handled = new CopyOnWriteArrayList<>();
        CompensationRelay relay = new CompensationRelay(log, List.of(handler("inventory", handled)));

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    start.await();
                    relay.pollOnce();
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(handled).containsExactly("p");
    }

    private static CompensationRecord record(String actionKey,
                                             CompensationStatus status,
                                             String handler,
                                             String payload) {
        return new CompensationRecord(actionKey, status, handler, payload, 0, Instant.now());
    }

    private static ICompensationHandler handler(String name, List<String> handled) {
        return new ICompensationHandler() {
            @Override
            public String actionName() {
                return name;
            }

            @Override
            public void compensate(String payload) {
                handled.add(payload);
            }
        };
    }
}
