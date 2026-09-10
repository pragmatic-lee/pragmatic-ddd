package io.pragmatic.ddd.repository.reconciliation;

/**
 * 聚合专属对账接线贡献：各聚合在专属配置中产出该 Bean，
 * 由通用 ReconciliationConfig 在共享 Registry 构建阶段统一 apply。
 * 因仓储服务的聚合类型在运行时被泛型擦除，注册仓储必须显式传入聚合类型，
 * 故该接线无法由框架自动推导，需由各聚合在此处声明。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface ReconciliationContribution {
    void contribute(ReconciliationRegistry registry);
}
