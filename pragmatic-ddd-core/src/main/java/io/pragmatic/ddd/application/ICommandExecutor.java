package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 命令执行器契约：封装"领域逻辑 → 规则校验 → 持久化 → 事件分发"的标准流程。
 *
 * <p>现有 {@link CommandExecutor}（保存后直接发布）与
 * {@code io.pragmatic.ddd.application.outbox.OutboxCommandExecutor}
 * （同事务落 outbox + 提交后推送）均实现本接口，可互换注入到
 * {@link AbstractApplicationService}。</p>
 *
 * @author Li XiaoJing
 * @since 2.4.0
 */
public interface ICommandExecutor {

    <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic);
}
