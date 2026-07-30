package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 工作单元契约：封装"跨聚合根事务编排——领域逻辑 → 规则校验 → 持久化 → 事件收集 → 事件分发"。
 *
 * @author wizard-lee
 */
public interface IUnitOfWork extends AutoCloseable {

    /** 注册一个聚合根操作（链式调用）。 */
    <ID, T extends AggregateRoot<ID>> IUnitOfWork register(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic);

    /** 提交工作单元：统一校验 → 持久化 → 收集 → 分发事件。 */
    void commit();

    /**
     * 对已注册的全部条目做试跑：逐条执行领域逻辑与规则校验，不落库、不发事件。
     * 默认不支持，由 {@link AbstractUnitOfWork} 覆写为真实实现。
     * 对应设计文档《应用服务层 Try-run（Dry-run）能力支持》5.3 节。
     *
     * @return 聚合全部条目校验结论的试跑结果
     */
    default DryRunResult tryCommit() {
        throw new UnsupportedOperationException(
                "tryCommit not supported by " + getClass().getName());
    }

    /** 未提交时自动清理事件，防止内存泄漏。 */
    @Override
    void close();
}
