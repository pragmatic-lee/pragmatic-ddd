package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.internal.defaults.SubscriberOrderManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证订阅者顺序管理器的依赖注册、后继查找、循环依赖检测与并发安全。
 *
 * @author wizard-lee
 */
class SubscriberOrderManagerTest {

    private final SubscriberOrderManager manager = new SubscriberOrderManager();

    @Test
    void registerDependency_rootSubscriber_foundByFindRootSubscribers() {
        manager.registerDependency("evt", "a", null);
        assertThat(manager.findRootSubscribers("evt")).containsExactly("a");
    }

    @Test
    void registerDependency_chain_aToBAndC() {
        manager.registerDependency("evt", "a", null);
        manager.registerDependency("evt", "b", "a");
        manager.registerDependency("evt", "c", "a");
        assertThat(manager.findNextSubscribers("evt", "a")).containsExactlyInAnyOrder("b", "c");
        assertThat(manager.findRootSubscribers("evt")).containsExactly("a");
    }

    @Test
    void registerDependency_multiLevel_aToBToD() {
        manager.registerDependency("evt", "a", null);
        manager.registerDependency("evt", "b", "a");
        manager.registerDependency("evt", "d", "b");
        assertThat(manager.findNextSubscribers("evt", "b")).containsExactly("d");
    }

    @Test
    void getDependencyEdges_returnsRegisteredEdges() {
        manager.registerDependency("evt", "a", null);
        manager.registerDependency("evt", "b", "a");
        List<ISubscriberOrderManager.OrderEdge> edges = manager.getDependencyEdges("evt");
        assertThat(edges).hasSize(2);
    }

    @Test
    void registerDependency_selfDependency_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> manager.registerDependency("evt", "a", "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerDependency_cyclicDependency_throwsIllegalStateException() {
        manager.registerDependency("evt", "a", null);
        manager.registerDependency("evt", "b", "a");
        assertThatThrownBy(() -> manager.registerDependency("evt", "a", "b"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerDependency_concurrentRegistration_noEdgeLost() throws InterruptedException {
        int threadCount = 8;
        int depsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger registered = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int base = t * depsPerThread;
            tasks.add(() -> {
                try {
                    start.await();
                    for (int i = 0; i < depsPerThread; i++) {
                        int idx = base + i;
                        manager.registerDependency("evt", "s" + idx, "root");
                        registered.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        tasks.forEach(pool::submit);
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(registered.get()).isEqualTo(threadCount * depsPerThread);
        assertThat(manager.getDependencyEdges("evt")).hasSize(threadCount * depsPerThread);
    }
}
