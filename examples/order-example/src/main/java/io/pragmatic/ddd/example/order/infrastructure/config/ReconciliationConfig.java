package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.example.order.infrastructure.config.reconciliation.InMemoryTimeWindowReconcileDedup;
import io.pragmatic.ddd.repository.IReadModelReplica;
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
 * 并通过集合注入收编所有副本（即读侧源）与仓储接线贡献，不感知具体聚合类型。
 * 各聚合只需让源实现 {@link IReadModelReplica}（读侧源天然满足）并在专属配置中提供
 * ReconciliationContribution 的 Bean 即可被自动登记。
 *
 * <p>对账触发为事件驱动：写模型落库后经 Outbox 投递数据同步事件，由 DELAYED 策略的
 * 订阅者单条复核自愈。框架不提供定时扫描与候选 ID 来源，「何时对账」由业务方决定；
 * 批量对账（新增副本回填 / 运维修复存量漂移）由调用方自备 ID 集合调用
 * {@link ReconciliationManager#reconcileBatch(Class, java.util.Collection)}。</p>
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
}
