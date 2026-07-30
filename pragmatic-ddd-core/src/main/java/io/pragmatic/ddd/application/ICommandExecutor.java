package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.RuleException;
import io.pragmatic.ddd.repository.IRepository;

import java.util.function.Consumer;

/**
 * 命令执行器契约：封装"领域逻辑 → 规则校验 → 持久化 → 事件分发"的标准流程，
 * 并提供与之同源、零副作用的试跑（Dry-run）入口。
 *
 * @author wizard-lee
 */
public interface ICommandExecutor {

    <ID, T extends AggregateRoot<ID>> T execute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic);

    /**
     * 试跑单聚合根命令：执行与 execute 完全相同的领域逻辑与规则校验，
     * 但跳过持久化与事件分发，以结构化结果返回校验结论。
     * 对应设计文档《应用服务层 Try-run（Dry-run）能力支持》5.2 节。
     *
     * @param aggregateRoot 本次试跑专用的聚合根实例（试跑后不得再用于真实执行）
     * @param rule          业务规则，为 null 时视为无规则约束
     * @param repository    仓储，试跑不使用，保持与 execute 签名对称
     * @param domainLogic   领域逻辑
     * @return 试跑结果；仅规则类异常被转译为未通过，其余异常照常上抛
     */
    default <ID, T extends AggregateRoot<ID>> DryRunResult tryExecute(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        try {
            // 1. 执行领域逻辑
            domainLogic.accept(aggregateRoot);

            // 2. 规则校验：未通过时以结构化结果返回，不抛异常
            if (rule != null && !aggregateRoot.satisfiesRule(rule)) {
                return DryRunResult.reject(aggregateRoot.getBrokenRules());
            }
            return DryRunResult.pass();
        } catch (RuleException ignored) {
            // 领域逻辑内部主动抛出的规则异常同样视为"未通过"
            return DryRunResult.reject(aggregateRoot.getBrokenRules());
        } finally {
            // 丢弃试跑期间暂存的领域事件与操作记录，保证零副作用外泄
            aggregateRoot.clearWorkUnitState();
        }
    }
}
