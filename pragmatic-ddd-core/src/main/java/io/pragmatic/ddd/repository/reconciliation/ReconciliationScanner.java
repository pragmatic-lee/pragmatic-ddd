package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * 对账定时扫描器（框架提供，聚合无关）。
 * 通过 ScheduledExecutorService 周期触发 {@link #scanOnce()}，每次扫描由固定线程池按批次并行对账，
 * 在最小内存占用下覆盖全部候选聚合的读模型一致性。
 *
 * <p>设计要点：</p>
 * <ul>
 *     <li>调度（scheduler）与执行（workers）解耦：scheduler 单线程仅负责周期触发，
 *         workers 固定线程池承担并行对账。</li>
 *     <li>批次内并行、整批完成后才取下一批，内存中在途任务始终限制在 batchSize 量级。</li>
 *     <li>并行度默认 max(CPU 核数, 8)，reconcile 为 I/O 密集型，可由 concurrency 显式覆盖。</li>
 * </ul>
 *
 * @author wizard-lee
 */
public class ReconciliationScanner {

    private static final Logger log = Logger.getLogger(ReconciliationScanner.class.getName());

    private final ReconciliationManager manager;
    private final List<IReconciliationCandidateProvider<?>> providers;
    private final ScanConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workers;

    public ReconciliationScanner(ReconciliationManager manager,
                                 List<IReconciliationCandidateProvider<?>> providers,
                                 ScanConfig config) {
        this.manager = manager;
        this.providers = providers;
        this.config = config;
        int concurrency = config.concurrency() > 0
                ? config.concurrency()
                : Math.max(Runtime.getRuntime().availableProcessors(), 8);
        this.workers = Executors.newFixedThreadPool(concurrency);
    }

    /** 启动周期扫描。 */
    public void start() {
        scheduler.scheduleWithFixedDelay(
                this::scanOnce,
                config.initialDelaySeconds(),
                config.intervalSeconds(),
                TimeUnit.SECONDS);
    }

    /** 单次全量扫描（启动回填 / 手动触发）。 */
    @SuppressWarnings("unchecked")
    public void scanOnce() {
        for (IReconciliationCandidateProvider<?> provider : providers) {
            Class<? extends AggregateRoot<Object>> type =
                    (Class<? extends AggregateRoot<Object>>) provider.aggregateType();
            List<?> batch;
            while (!(batch = provider.nextBatch(config.batchSize())).isEmpty()) {
                List<Callable<Void>> tasks = new ArrayList<>(batch.size());
                for (Object id : batch) {
                    tasks.add(() -> reconcileOne(type, id));
                }
                try {
                    workers.invokeAll(tasks);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private Void reconcileOne(Class<? extends AggregateRoot<Object>> type, Object id) {
        try {
            manager.reconcile(type, id);
        } catch (Exception e) {
            log.warning("reconcile failed: type=" + type.getSimpleName()
                    + ", id=" + id + ", cause=" + e.getMessage());
        }
        return null;
    }

    public void shutdown() {
        scheduler.shutdownNow();
        workers.shutdownNow();
    }
}
