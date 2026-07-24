package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 工作单元契约：封装"跨聚合根的事务编排 —— 领域逻辑 → 规则校验 → 持久化 → 事件收集 → 事件分发"。
 *
 * <p>现有 {@link UnitOfWork}（全部 save 后统一 {@code publishList}）与
 * {@code io.pragmatic.ddd.application.outbox.OutboxUnitOfWork}
 * （同事务落 outbox + 提交后推送）均实现本接口，可互换注入到
 * {@link AbstractApplicationService#beginUnitOfWork()}。</p>
 *
 * @author Li XiaoJing
 * @since 2.5.0
 */
public interface IUnitOfWork extends AutoCloseable {

    /**
     * 注册一个聚合根操作（链式调用）。
     *
     * @return this（类型提升为 {@link IUnitOfWork}，支持链式 {@code register(...).register(...)}）
     */
    <ID, T extends AggregateRoot<ID>> IUnitOfWork register(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic);

    /** 提交工作单元：统一校验 → 持久化 → 收集 → 分发事件。 */
    void commit();

    /** 未提交时自动清理事件，防止内存泄漏。 */
    @Override
    void close();
}
