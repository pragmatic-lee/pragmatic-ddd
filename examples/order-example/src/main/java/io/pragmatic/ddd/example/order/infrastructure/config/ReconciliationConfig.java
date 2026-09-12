package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.example.order.infrastructure.config.reconciliation.InMemoryTimeWindowReconcileDedup;
import io.pragmatic.ddd.repository.IReadModelReplica;
import io.pragmatic.ddd.repository.reconciliation.IReconcileDedup;
import io.pragmatic.ddd.repository.reconciliation.IReconciliationCandidateProvider;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationContribution;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationManager;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationRegistry;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationScanner;
import io.pragmatic.ddd.repository.reconciliation.ScanConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 通用对账配置（聚合无关）：提供共享的 ReconciliationRegistry 与 ReconciliationManager，
 * 并通过集合注入收编所有副本（即读侧源）与仓储接线贡献，不感知具体聚合类型。
 * 各聚合只需让源实现 {@link IReadModelReplica}（读侧源天然满足）并在专属配置中提供
 * ReconciliationContribution 的 Bean 即可被自动登记。
 *
 * @author wizard-lee
 */
@Configuration
public class ReconciliationConfig {

    @Bean
    public ReconciliationRegistry reconciliationRegistry(
            List<IReadModelReplica<?>> replicas,
            List<ReconciliationContribution> contributions) {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerReplicas(replicas);
        contributions.forEach(contribution -> contribution.contribute(registry));
        return registry;
    }

    @Bean
    public ReconciliationManager reconciliationManager(
            ReconciliationRegistry registry,
            IReconcileDedup reconcileDedup) {
        return new ReconciliationManager(registry, reconcileDedup);
    }

    /**
     * 默认去重：进程内时间窗。多实例部署可另行提供 IReconcileDedup Bean 覆盖本默认实现。
     */
    @Bean
    public IReconcileDedup reconcileDedup(
            @Value("${order.reconcile.dedup.window-seconds:60}") long windowSeconds) {
        return new InMemoryTimeWindowReconcileDedup(windowSeconds * 1000L);
    }

    /**
     * 对账定时扫描器：收编所有候选 ID 提供者，周期全量对账。
     * 仅运行于单一实例（避免多实例重复全量扫描造成的读放大），生产环境仅对一台机器开启。
     * 关闭上下文时由 destroyMethod 调用 shutdown 释放线程池。
     */
    @Bean(destroyMethod = "shutdown")
    public ReconciliationScanner reconciliationScanner(
            ReconciliationManager reconciliationManager,
            List<IReconciliationCandidateProvider<?>> candidateProviders,
            @Value("${order.reconcile.scan.initial-delay-seconds:30}") long initialDelaySeconds,
            @Value("${order.reconcile.scan.interval-seconds:300}") long intervalSeconds,
            @Value("${order.reconcile.scan.batch-size:200}") int batchSize,
            @Value("${order.reconcile.scan.concurrency:0}") int concurrency) {
        ScanConfig scanConfig = new ScanConfig(initialDelaySeconds, intervalSeconds, batchSize, concurrency);
        ReconciliationScanner scanner = new ReconciliationScanner(reconciliationManager, candidateProviders, scanConfig);
        scanner.start();
        return scanner;
    }
}
