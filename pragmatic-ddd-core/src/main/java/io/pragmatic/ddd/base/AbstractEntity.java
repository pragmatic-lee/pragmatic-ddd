package io.pragmatic.ddd.base;


import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationException;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.operation.TriggeredOperations;
import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.event.DomainEventCollector;
import io.pragmatic.ddd.event.IDomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Getter
@Setter(value = AccessLevel.PROTECTED)
public abstract class AbstractEntity<T> extends BrokenRuleObject implements IEntity<T> {

    private T entityId;

    private boolean entityDelete;
    private boolean isNewEntity = false;
    private long oldVersion = 1;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newVersionIsGenerate = false;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private long newVersion = 0;

    // ===== 审计字段 =====
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    private final DomainEventCollector eventCollector = new DomainEventCollector();
    private TriggeredOperations triggeredOperations;

    /** 最近一次 recordOperation 记录的操作（因果归属用，单值指针） */
    private EntityOperation lastRecordedOperation;

    @Override
    protected abstract BrokenRuleMessage getBrokenRuleMessages();

    protected abstract OperationRegistry entityOperations();

    protected <V> V setAndReturnOld(Consumer<V> set, Supplier<V> getOld, V newValue) {
        V old = getOld.get();
        set.accept(newValue);

        return old;
    }

    protected <V> CompareAndSetInfo<V> compareAndSet(V newValue, V oldValue, Consumer<V> set) {
        boolean equals = Objects.equals(newValue, oldValue);
        if (!equals) {
            set.accept(newValue);
        }
        return new CompareAndSetInfo<>(equals, newValue, oldValue);
    }


    /**
     * 路径①：默认取最近一次 recordOperation 的操作作为成因。
     */
    protected void publishEvent(BaseDomainEvent event) {
        event.operationCode = this.resolveOperationCode();
        event.version = this.getNewVersion();
        this.eventCollector.collect(event);
    }

    /**
     * 路径②：显式指定成因操作，优先级最高（保持不变）。
     */
    protected void publishEvent(BaseDomainEvent event, EntityOperation triggerOperation) {
        event.operationCode = triggerOperation.code();
        event.version = this.getNewVersion();
        this.eventCollector.collect(event);
    }

    /**
     * 路径③：延迟事件——发布时刻捕获成因，物化时回填 operationCode + version。
     * <p>归属"当下最近一次操作"（与路径①语义一致），version 因 getNewVersion() 幂等，物化时回填与发布时取值一致。</p>
     */
    protected void publishEvent(Supplier<IDomainEvent> eventSupplier) {
        String capturedCode = this.resolveOperationCode();
        this.eventCollector.collectDelayed(() -> {
            IDomainEvent e = eventSupplier.get();
            if (e instanceof BaseDomainEvent base) {
                base.operationCode = capturedCode;
                base.version = this.getNewVersion();
            }
            return e;
        });
    }

    public List<IDomainEvent> getDomainEvents() {
        return this.eventCollector.getEvents();
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
        if (this.entityOperations() != null) {
            throw new OperationException(
                    "发布事件前必须先 recordOperation，或使用 publishEvent(event, operation) 显式指定成因操作："
                            + this.getClass().getSimpleName());
        }
        return null;
    }

    /**
     * 清空一次工作单元的全部临时状态：领域事件 + 已触发操作 + 因果指针。
     * <p>应用层在事件分发完成后调用，防止同一实体实例被复用时状态残留。</p>
     */
    public void clearWorkUnitState() {
        this.eventCollector.clear();
        if (this.triggeredOperations != null) {
            this.triggeredOperations.clear();
        }
        this.lastRecordedOperation = null;
    }

    /**
     * 清空已收集的领域事件。
     * @deprecated 语义已并入 {@link #clearWorkUnitState()}，保留仅为调用点平滑过渡，建议改用后者。
     */
    public void clearDomainEvents() {
        this.clearWorkUnitState();
    }

    protected void recordOperation(EntityOperation operation) {
        this.triggeredOperations().put(operation);   // 多值收集：不变
        this.lastRecordedOperation = operation;       // 新增：更新因果指针
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
            OperationRegistry registry = this.entityOperations();
            if (registry == null) {
                throw new OperationException(
                        "实体未启用 operation 体系（entityOperations() 返回 null），不可调用 recordOperation/hasOperation："
                                + this.getClass().getSimpleName());
            }
            this.triggeredOperations = new TriggeredOperations(registry);
        }
        return this.triggeredOperations;
    }
    public long getNewVersion() {
        if (!this.newVersionIsGenerate) {
            long v = this.oldVersion;
            this.newVersion = ++v;
            this.newVersionIsGenerate = true;
        }
        return this.newVersion;
    }

    // ============ 审计时间戳 ============

    /**
     * 标记实体已创建，设置 createdAt 和 updatedAt 为当前时间。
     * 子类在构造函数末尾调用一次。
     * 如需记录创建人，在调用此方法前通过 {@code setCreatedBy()} / {@code setUpdatedBy()} 赋值。
     */
    protected void markCreated() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 标记实体已被修改，更新 updatedAt 为当前时间。
     * 子类在修改状态的方法中调用。
     * 如需记录修改人，在调用此方法前通过 {@code setUpdatedBy()} 赋值。
     */
    protected void markModified() {
        this.updatedAt = LocalDateTime.now();
    }

    // ============ 实体等同性（基于身份标识 ID） ============

    /**
     * 基于身份标识 ID 判断实体等同性。
     * <p>如果两个实体 ID 都非 null 且相等，则视为同一个实体。
     * 如果任一 ID 为 null（尚未持久化），则视为不相等。</p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractEntity<?> that)) {
            return false;
        }
        T thisId = this.getEntityId();
        Object thatId = that.getEntityId();
        if (thisId != null && thatId != null) {
            return thisId.equals(thatId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        T thisId = this.getEntityId();
        return thisId != null ? thisId.hashCode() : super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + getEntityId() + "}";
    }
}
