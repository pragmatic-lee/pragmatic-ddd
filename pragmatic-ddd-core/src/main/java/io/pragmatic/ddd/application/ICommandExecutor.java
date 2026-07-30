package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 命令执行器契约：封装"领域逻辑 → 规则校验 → 持久化 → 事件分发"的标准流程。
 *
 * @author wizard-lee
 */
public interface ICommandExecutor {

    <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic);
}
