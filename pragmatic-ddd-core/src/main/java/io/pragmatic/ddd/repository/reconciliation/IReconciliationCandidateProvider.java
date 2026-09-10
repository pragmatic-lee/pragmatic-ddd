package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.AggregateRoot;

import java.util.List;

/**
 * 对账候选聚合 ID 来源（SPI，由接入方实现）。
 * 扫描器周期调用 {@link #nextBatch(int)} 推进游标，直到返回空集合表示本轮扫描结束。
 *
 * @param <ID> 聚合标识类型
 * @author wizard-lee
 */
public interface IReconciliationCandidateProvider<ID> {

    /** 本提供者负责的聚合类型。 */
    Class<? extends AggregateRoot<ID>> aggregateType();

    /**
     * 返回下一批候选聚合 ID（实现需保证批次间游标推进，避免重复或遗漏）。
     * 返回空集合表示本轮扫描结束。
     *
     * @param batchSize 单批最大数量
     * @return 候选 ID 列表
     */
    List<ID> nextBatch(int batchSize);
}
