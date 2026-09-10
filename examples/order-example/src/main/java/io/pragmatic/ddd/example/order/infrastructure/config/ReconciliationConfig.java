package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.example.order.infrastructure.config.reconciliation.InMemoryTimeWindowReconcileDedup;
import io.pragmatic.ddd.repository.reconciliation.IReadModelResynchronizer;
import io.pragmatic.ddd.repository.reconciliation.IReadModelVersionResolver;
import io.pragmatic.ddd.repository.reconciliation.IReconcileDedup;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationContribution;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationManager;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 通用对账配置（聚合无关）：提供共享的 ReconciliationRegistry 与 ReconciliationManager，
 * 并通过集合注入收编所有聚合的版本解析器、补同步器与仓储接线贡献，不感知具体聚合类型。
 * 各聚合只需在专属配置中提供 IReadModelVersionResolver / IReadModelResynchronizer /
 * ReconciliationContribution 的 Bean 即可被自动登记。
 *
 * @author wizard-lee
 */
@Configuration
public class ReconciliationConfig {

    @Bean
    public ReconciliationRegistry reconciliationRegistry(
            List<IReadModelVersionResolver<?>> resolvers,
            List<IReadModelResynchronizer<?>> resyncers,
            List<ReconciliationContribution> contributions) {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        resolvers.forEach(registry::registerResolver);
        resyncers.forEach(registry::registerResynchronizer);
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
}
