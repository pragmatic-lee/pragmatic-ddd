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
 * 聚合根基类。
 *
 * <p>对应重构计划 3.3 节：继承数据（{@link AbstractEntity}）+ 组合规则（{@link BrokenRuleObject}）+ 聚合能力。</p>
 *
 * <p>聚合根是 DDD 聚合的唯一外部入口点，负责维护聚合内部不变性约束。
 * 所有需要通过仓储持久化的实体都必须继承此类。聚合级能力（规则校验、版本号、
 * 领域事件、操作追踪、工作单元清理、新建标记）集中在本类承载。</p>
 *
 * <p>与 AbstractEntity 的关系：</p>
 * <ul>
 *   <li>聚合内部的值对象 → 纯 POJO，不继承任何框架类（大多数情况）</li>
 *   <li>极少数需要独立 ID 的内部实体 → 继承 AbstractEntity（纯数据容器：身份、软删、审计）</li>
 *   <li>聚合根 → 继承 AggregateRoot（有完整仓储能力，编译期约束入口）</li>
 * </ul>
 *
 * <p>标准用法：</p>
 * <pre>{@code
 * // 聚合根
 * public class Order extends AggregateRoot<Long> {
 *     private List<OrderLine> lines;      // ← 值对象，纯 POJO
 *     private Address shippingAddress;    // ← 值对象，纯 POJO
 * }
 * }</pre>
 *
 * @param <T> 标识类型
 * @author Li XiaoJing
 * @since 2.1.0
 */
public abstract class AggregateRoot<T> extends AbstractEntity<T> {

    // ============ 组合的规则违反收集器（重构计划 3.3 第 2 点） ============

    private final transient BrokenRuleObject ruleValidator = new BrokenRuleObject();

    /** 首次使用时惰性注入子类提供的规则注册表与宿主聚合引用。 */
    private BrokenRuleObject ruleValidator() {
        if (this.ruleValidator.brokenRuleRegistry() == null) {
            this.ruleValidator.setBrokenRuleRegistry(this.brokenRuleRegistry());
        }
        this.ruleValidator.setSource(this);
        return this.ruleValidator;
    }

    // ============ 抽象方法（由 AbstractEntity 上移，重构计划 3.3 第 3 点） ============

    protected abstract BrokenRuleRegistry brokenRuleRegistry();

    protected abstract OperationRegistry operationRegistry();

    // ============ 版本控制（重构计划 3.3 第 5 点） ============

    @Getter
    private long oldVersion = 1;
    private boolean newVersionIsGenerate = false;
    private long newVersion = 0;

    // ============ 新建标记（重构计划 3.3 第 9 点 / 2.2 命名对齐） ============

    private boolean isNew = false;

    // ============ 领域事件与操作追踪（重构计划 3.3 第 6、7 点） ============

    private final TriggeredEvents triggeredEvents = new TriggeredEvents();
    private TriggeredOperations triggeredOperations;

    /** 最近一次 recordOperation 记录的操作（因果归属用，单值指针） */
    private EntityOperation lastRecordedOperation;

    // ============ 规则校验委托（重构计划 3.3 第 4 点，public 供 rules 包跨包调用） ============

    /**
     * 使用指定的规则集合执行校验（以聚合根自身作为 model）。
     * 校验失败时，规则违反信息通过 {@link #addBrokenRule} 收集到组合的 BrokenRuleObject 中，
     * 后续可通过 {@link #throwBrokenRuleException()} 或 {@link #exceptionCause()} 获取。
     *
     * @param rule 满足 IRule 约束的规则对象（如 EntityRule），为 null 时视为校验通过
     * @return true 表示通过校验，false 表示存在规则违反
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean satisfiesRule(IRule<?> rule) {
        return rule != null && ((IRule) rule).satisfiesRule(this);
    }

    public void throwBrokenRuleException() {
        ruleValidator().throwBrokenRuleException();
    }

    public void throwBrokenRuleAggregateException() {
        ruleValidator().throwBrokenRuleAggregateException();
    }

    public void clearBrokenRules() {
        ruleValidator().clearBrokenRules();
    }

    public List<BrokenRule> getBrokenRules() {
        return ruleValidator().getBrokenRules();
    }

    public BrokenRuleException exceptionCause() {
        return ruleValidator().exceptionCause();
    }

    public BrokenRuleAggregateException aggregateExceptionCause() {
        return ruleValidator().aggregateExceptionCause();
    }

    public void addBrokenRule(MessageCode code) {
        ruleValidator().addBrokenRule(code);
    }

    public void addParamBrokenRule(MessageCode code, Object[] params, boolean isAutoFormat) {
        ruleValidator().addParamBrokenRule(code, params, isAutoFormat);
    }

    // ============ 领域事件（重构计划 3.3 第 6 点，原样自 AbstractEntity 迁移） ============

    /**
     * 路径①：默认取最近一次 recordOperation 的操作作为成因。
     */
    protected void collectEvent(BaseDomainEvent event) {
        event.operationCode = this.resolveOperationCode();
        event.version = this.getNewVersion();
        this.triggeredEvents.collect(event);
    }

    /**
     * 路径②：显式指定成因操作，优先级最高（保持不变）。
     */
    protected void collectEvent(BaseDomainEvent event, EntityOperation triggerOperation) {
        event.operationCode = triggerOperation.code();
        event.version = this.getNewVersion();
        this.triggeredEvents.collect(event);
    }

    /**
     * 路径③：延迟事件——发布时刻捕获成因，物化时回填 operationCode + version。
     * <p>归属"当下最近一次操作"（与路径①语义一致），version 因 getNewVersion() 幂等，物化时回填与发布时取值一致。</p>
     */
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

    public List<IDomainEvent> getDomainEvents() {
        return this.triggeredEvents.getEvents();
    }

    /**
     * 解析当前事件应归属的 operationCode。
     * <ul>
     *   <li>已 recordOperation → 返回最近一次操作编码；</li>
     *   <li>启用了 operation 体系（entityOperations() 非 null）却未 record → fail-fast 抛异常；</li>
     *   <li>未启用 operation 体系（entityOperations() 为 null）→ 返回 null（不参与归属）。</li>
     * </ul>
     */
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

    // ============ 工作单元清理（重构计划 3.3 第 8 点） ============

    /**
     * 清空一次工作单元的全部临时状态：领域事件 + 已触发操作 + 因果指针。
     * <p>应用层在事件分发完成后调用，防止同一实体实例被复用时状态残留。</p>
     */
    public void clearWorkUnitState() {
        this.triggeredEvents.clear();
        if (this.triggeredOperations != null) {
            this.triggeredOperations.clear();
        }
        this.lastRecordedOperation = null;
    }

    // ============ 操作追踪（重构计划 3.3 第 7 点，原样自 AbstractEntity 迁移） ============

    protected void recordOperation(EntityOperation operation) {
        this.triggeredOperations().put(operation);   // 多值收集：不变
        this.lastRecordedOperation = operation;       // 更新因果指针
    }

    public boolean hasOperation(EntityOperation operation) {
        return this.triggeredOperations().contains(operation);
    }

    public boolean hasAllOperations(EntityOperation... operations) {
        return this.triggeredOperations().containsAll(operations);
    }

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

    // ============ 版本号（重构计划 3.3 第 5 点，原样自 AbstractEntity 迁移） ============

    public long getNewVersion() {
        if (!this.newVersionIsGenerate) {
            long v = this.oldVersion;
            this.newVersion = ++v;
            this.newVersionIsGenerate = true;
        }
        return this.newVersion;
    }

    // ============ 新建标记（重构计划 2.2：isNew/markNew 替代 isNewEntity/setNewEntity） ============

    public boolean isNew() {
        return this.isNew;
    }

    public void markNew() {
        this.isNew = true;
    }
}
