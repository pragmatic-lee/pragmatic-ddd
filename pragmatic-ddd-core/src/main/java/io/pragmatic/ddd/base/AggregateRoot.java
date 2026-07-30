package io.pragmatic.ddd.base;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.TriggeredEvents;
import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.operation.TriggeredOperations;
import lombok.Getter;

import java.util.List;
import java.util.function.Supplier;

/**
 * 聚合根基类，继承 {@link AbstractEntity} 并组合 {@link BrokenRuleObject}。
 * 作为 DDD 聚合的唯一外部入口点，集中维护不变性约束所需的聚合级能力：
 * 规则校验、乐观锁版本号、领域事件收集、操作追踪、工作单元清理与新建标记。
 * 所有需经仓储持久化的实体都应继承此类。
 *
 * @param <T> 标识类型
 * @author wizard-lee
 */
public abstract class AggregateRoot<T> extends AbstractEntity<T> {

    // ============ 组合的规则违反收集器 ============

    private final transient BrokenRuleObject ruleValidator = new BrokenRuleObject();

    private BrokenRuleObject ruleValidator() {
        if (this.ruleValidator.brokenRuleRegistry() == null) {
            this.ruleValidator.setBrokenRuleRegistry(this.brokenRuleRegistry());
        }
        this.ruleValidator.setSource(this);
        return this.ruleValidator;
    }

    // ============ 抽象方法 ============

    /** 子类提供规则注册表。 */
    protected abstract BrokenRuleRegistry brokenRuleRegistry();

    /** 子类提供操作注册表；返回 null 表示不启用操作体系。 */
    protected abstract OperationRegistry operationRegistry();

    // ============ 版本控制 ============

    @Getter
    private long oldVersion = 1;
    private boolean newVersionIsGenerate = false;
    private long newVersion = 0;

    // ============ 新建标记 ============

    private boolean isNew = false;

    // ============ 领域事件与操作追踪 ============

    private final TriggeredEvents triggeredEvents = new TriggeredEvents();
    private TriggeredOperations triggeredOperations;

    /** 最近一次 recordOperation 记录的操作（因果归属用，单值指针）。 */
    private EntityOperation lastRecordedOperation;

    // ============ 规则校验委托（public 供 rules 包跨包调用） ============

    /**
     * 使用指定的规则对象执行校验（以聚合根自身作为 model）。
     *
     * @param rule 满足 IRule 约束的规则对象（如 EntityRule），为 null 时视为校验通过
     * @return true 表示通过校验，false 表示存在规则违反
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean satisfiesRule(IRule<?> rule) {
        return rule != null && ((IRule) rule).satisfiesRule(this);
    }

    /** 若存在规则违反则抛出单条异常。 */
    public void throwBrokenRuleException() {
        ruleValidator().throwBrokenRuleException();
    }

    /** 若存在规则违反则抛出聚合异常。 */
    public void throwBrokenRuleAggregateException() {
        ruleValidator().throwBrokenRuleAggregateException();
    }

    /** 清空已收集的规则违反。 */
    public void clearBrokenRules() {
        ruleValidator().clearBrokenRules();
    }

    /** 返回已收集的规则违反列表（只读）。 */
    public List<BrokenRule> getBrokenRules() {
        return ruleValidator().getBrokenRules();
    }

    /** 返回首个规则违反对应的单条异常，无违反时返回 null。 */
    public BrokenRuleException exceptionCause() {
        return ruleValidator().exceptionCause();
    }

    /** 返回聚合全部规则违反的异常，无违反时返回 null。 */
    public BrokenRuleAggregateException aggregateExceptionCause() {
        return ruleValidator().aggregateExceptionCause();
    }

    /** 向收集器追加一条规则违反。 */
    public void addBrokenRule(MessageCode code) {
        ruleValidator().addBrokenRule(code);
    }

    /** 向收集器追加一条支持参数格式化的规则违反。 */
    public void addParamBrokenRule(MessageCode code, Object[] params, boolean isAutoFormat) {
        ruleValidator().addParamBrokenRule(code, params, isAutoFormat);
    }

    // ============ 领域事件 ============

    /** 收集领域事件；成因默认取最近一次 recordOperation 的操作。 */
    protected void collectEvent(BaseDomainEvent event) {
        event.operationCode = this.resolveOperationCode();
        event.version = this.getNewVersion();
        this.triggeredEvents.collect(event);
    }

    /** 收集领域事件，并显式指定成因操作（优先级最高）。 */
    protected void collectEvent(BaseDomainEvent event, EntityOperation triggerOperation) {
        event.operationCode = triggerOperation.code();
        event.version = this.getNewVersion();
        this.triggeredEvents.collect(event);
    }

    /** 收集延迟事件，发布时捕获成因并回填 operationCode 与 version。 */
    protected void collectEvent(Supplier<IDomainEvent> eventSupplier) {
        String capturedCode = this.resolveOperationCode();
        this.triggeredEvents.collectDelayed(() -> {
            IDomainEvent e = eventSupplier.get();
            if (e instanceof BaseDomainEvent base) {
                base.operationCode = capturedCode;
                base.version = this.getNewVersion();
            }
            return e;
        });
    }

    /** 返回本工作单元已收集的领域事件。 */
    public List<IDomainEvent> getDomainEvents() {
        return this.triggeredEvents.getEvents();
    }

    private String resolveOperationCode() {
        if (this.lastRecordedOperation != null) {
            return this.lastRecordedOperation.code();
        }
        if (this.operationRegistry() != null) {
            throw new OperationException(
                    "发布事件前必须先 recordOperation，或使用 collectEvent(event, operation) 显式指定成因操作："
                            + this.getClass().getSimpleName());
        }
        return null;
    }

    // ============ 工作单元清理 ============

    /** 清空工作单元临时状态（领域事件、已触发操作与因果指针），由应用层在事件分发完成后调用。 */
    public void clearWorkUnitState() {
        this.triggeredEvents.clear();
        if (this.triggeredOperations != null) {
            this.triggeredOperations.clear();
        }
        this.lastRecordedOperation = null;
    }

    // ============ 操作追踪 ============

    /** 记录一次操作，更新多值集合与因果指针。 */
    protected void recordOperation(EntityOperation operation) {
        this.triggeredOperations().put(operation);   // 多值收集：不变
        this.lastRecordedOperation = operation;       // 更新因果指针
    }

    /** 判断已触发的操作中是否包含指定操作。 */
    public boolean hasOperation(EntityOperation operation) {
        return this.triggeredOperations().contains(operation);
    }

    /** 判断已触发的操作是否包含全部指定操作。 */
    public boolean hasAllOperations(EntityOperation... operations) {
        return this.triggeredOperations().containsAll(operations);
    }

    /** 判断已触发的操作是否包含任一指定操作。 */
    public boolean hasAnyOperation(EntityOperation... operations) {
        return this.triggeredOperations().containsAny(operations);
    }

    private TriggeredOperations triggeredOperations() {
        if (this.triggeredOperations == null) {
            OperationRegistry registry = this.operationRegistry();
            if (registry == null) {
                throw new OperationException(
                        "实体未启用 operation 体系（entityOperations() 返回 null），不可调用 recordOperation/hasOperation："
                                + this.getClass().getSimpleName());
            }
            this.triggeredOperations = new TriggeredOperations(registry);
        }
        return this.triggeredOperations;
    }

    // ============ 版本号 ============

    /** 返回递增后的新版本号（幂等）。 */
    public long getNewVersion() {
        if (!this.newVersionIsGenerate) {
            long v = this.oldVersion;
            this.newVersion = ++v;
            this.newVersionIsGenerate = true;
        }
        return this.newVersion;
    }

    // ============ 新建标记 ============

    /** 判断该聚合根是否为新建状态。 */
    public boolean isNew() {
        return this.isNew;
    }

    /** 标记为新建状态。 */
    public void markNew() {
        this.isNew = true;
    }
}
