package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRule;
import io.pragmatic.ddd.base.BrokenRuleAggregateException;
import io.pragmatic.ddd.base.BrokenRuleException;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.base.RuleException;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.repository.IRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 工作单元抽象基类，固定"领域逻辑与规则校验（事务外）→ 持久化（事务内）→ 事件分发（事务外）"三阶段模板，
 * 事务边界由本基类在阶段二统一提供，子类实现 persistAndCollect 与 dispatchEvents 两个钩子，
 * 分别决定持久化内容与事件分发出路。
 *
 * @author wizard-lee
 */
public abstract class AbstractUnitOfWork implements IUnitOfWork {

    protected final List<UnitOfWorkEntry<?, ?>> entries = new ArrayList<>();
    private final TransactionOperations txOps;
    private boolean committed = false;
    private boolean dryRun = false;

    /**
     * 以事务抽象创建工作单元。事务是工作单元的固有语义：多个聚合（含同一聚合的多次操作）
     * 的持久化必然落在同一数据库事务内，任一条目失败整体回滚。
     *
     * @param txOps 事务抽象，由基础设施模块提供实现
     */
    protected AbstractUnitOfWork(TransactionOperations txOps) {
        this.txOps = txOps;
    }

    @Override
    public <ID, T extends AggregateRoot<ID>> IUnitOfWork register(
            T aggregateRoot,
            IRule<?> rule,
            IRepository<ID, T> repository,
            Consumer<T> domainLogic) {
        entries.add(new UnitOfWorkEntry<>(aggregateRoot, rule, repository, domainLogic));
        return this;
    }

    @Override
    public void commit() {
        if (committed) {
            throw new UnitOfWorkStateException("UnitOfWork already committed");
        }
        if (dryRun) {
            throw new UnitOfWorkStateException("UnitOfWork already consumed by tryCommit");
        }

        List<IDomainEvent> allEvents = new ArrayList<>();

        // 阶段一（事务外）：逐条执行领域逻辑与规则校验并汇总事件；任一违反直接终止，不进事务、不落库
        List<BrokenRule> brokenRules = this.validateAndCollect(allEvents);
        if (!brokenRules.isEmpty()) {
            throw new BrokenRuleAggregateException(this.toExceptions(brokenRules));
        }

        // 阶段二（事务内）：纯持久化，事务边界由基类统一提供，多聚合同事务
        this.txOps.execute(() -> {
            this.persistAndCollect(entries);
            return null;
        });

        // 阶段三（事务外）：统一分发事件，由钩子决定 publishList / publishAfterCommit
        this.dispatchEvents(allEvents);

        // 置位后置：阶段一/二失败时 close() 仍能清理暂存的事件，避免残留被后续误发
        committed = true;
    }

    /**
     * 试跑已注册的全部条目：逐条执行领域逻辑与规则校验，跳过持久化与事件分发。
     * 试跑会消费本工作单元，之后不可再 commit 或 tryCommit。
     */
    @Override
    public DryRunResult tryCommit() {
        if (committed) {
            throw new UnitOfWorkStateException("UnitOfWork already committed");
        }
        if (dryRun) {
            throw new UnitOfWorkStateException("UnitOfWork already consumed by tryCommit");
        }
        dryRun = true;

        List<IDomainEvent> ignoredEvents = new ArrayList<>();
        List<BrokenRule> allBrokenRules = new ArrayList<>();
        try {
            allBrokenRules.addAll(this.validateAndCollect(ignoredEvents));
        } finally {
            // 丢弃试跑期间暂存的领域事件与操作记录，保证零副作用外泄
            this.clearAllWorkUnitState();
        }

        if (allBrokenRules.isEmpty()) {
            return DryRunResult.pass();
        }
        return DryRunResult.reject(allBrokenRules);
    }

    /**
     * 阶段一：事务外逐条执行领域逻辑与规则校验，并把各聚合根已收集的事件汇总到 collected。
     * 规则校验中的外部调用与旧快照查询因此全部发生在事务之外，不占用数据库连接。
     *
     * @param collected 事件汇总容器，由本方法写入
     * @return 全部条目的规则违反明细，为空表示校验通过
     */
    protected List<BrokenRule> validateAndCollect(List<IDomainEvent> collected) {
        List<BrokenRule> brokenRules = new ArrayList<>();
        for (UnitOfWorkEntry<?, ?> entry : entries) {
            // 调用泛型辅助方法统一捕获通配符类型，规避 capture#1 ≠ capture#2 编译错误
            this.validateEntry(entry, collected, brokenRules);
        }
        return brokenRules;
    }

    private <ID, T extends AggregateRoot<ID>> void validateEntry(
            UnitOfWorkEntry<ID, T> entry,
            List<IDomainEvent> collected,
            List<BrokenRule> brokenRules) {
        try {
            // 1. 领域逻辑
            entry.domainLogic.accept(entry.aggregateRoot);
            // 2. 规则校验：未通过时收集明细，不中断其余条目
            if (entry.rule != null && !entry.aggregateRoot.satisfiesRule(entry.rule)) {
                brokenRules.addAll(entry.aggregateRoot.getBrokenRules());
            }
        } catch (RuleException ignored) {
            // 领域逻辑内部主动抛出的规则异常同样视为"未通过"
            brokenRules.addAll(entry.aggregateRoot.getBrokenRules());
        }
        // 3. 汇总事件：事务外完成，阶段二不再重复收集
        collected.addAll(entry.aggregateRoot.getDomainEvents());
    }

    private List<BrokenRuleException> toExceptions(List<BrokenRule> brokenRules) {
        return brokenRules.stream()
                .map(rule -> new BrokenRuleException(rule.getName(), rule.getDescription()))
                .toList();
    }

    @Override
    public void close() {
        if (!committed) {
            this.clearAllWorkUnitState();
        }
    }

    /** 清理全部条目暂存的领域事件与操作记录。 */
    private void clearAllWorkUnitState() {
        for (UnitOfWorkEntry<?, ?> entry : entries) {
            entry.aggregateRoot.clearWorkUnitState();
        }
    }

    /**
     * 钩子：逐条 save 并完成 outbox 落库，由基类在事务内调用，本钩子自身不开启事务。
     * 只做纯数据库写：领域逻辑与规则校验已由阶段一在事务外完成，
     * 事件也已由阶段一汇总并直接交给 {@link #dispatchEvents(List)}，本钩子不再接收事件列表。
     */
    protected abstract void persistAndCollect(List<UnitOfWorkEntry<?, ?>> entries);

    /** 钩子：阶段三，事务提交后统一分发事件。 */
    protected abstract void dispatchEvents(List<IDomainEvent> allEvents);

    /** 工作单元内已注册的操作条目。 */
    public static final class UnitOfWorkEntry<ID, T extends AggregateRoot<ID>> {
        public final T aggregateRoot;
        public final IRule<?> rule;
        public final IRepository<ID, T> repository;
        public final Consumer<T> domainLogic;

        public UnitOfWorkEntry(T aggregateRoot, IRule<?> rule,
                               IRepository<ID, T> repository, Consumer<T> domainLogic) {
            this.aggregateRoot = aggregateRoot;
            this.rule = rule;
            this.repository = repository;
            this.domainLogic = domainLogic;
        }
    }
}
