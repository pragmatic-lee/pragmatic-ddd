package io.pragmatic.ddd.base;


import io.pragmatic.ddd.action.Action;
import io.pragmatic.ddd.action.EntityAction;
import io.pragmatic.ddd.action.EntityActionCollector;
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

    private T id;

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
    private EntityActionCollector actionCollector;

    @Override
    protected abstract BrokenRuleMessage getBrokenRuleMessages();

    protected abstract EntityAction entityActions();

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


    protected void publishEvent(BaseDomainEvent event) {
        event.actionName = this.getCurrentActionCode();
        event.version = this.getNewVersion();
        this.eventCollector.pushEvent(event);
    }

    protected void publishEvent(BaseDomainEvent event, Action triggerAction) {
        event.actionName = triggerAction.getActionCode();
        event.version = this.getNewVersion();
        this.eventCollector.pushEvent(event);
    }

    protected void publishEvent(Supplier<IDomainEvent> eventSupplier) {
        this.eventCollector.pushDelayGenerateEvent(eventSupplier);
    }

    public List<IDomainEvent> getDomainEvents() {
        return this.eventCollector.getEventList();
    }

    /**
     * 获取当前触发的 Action 编码。
     * 默认返回 "UNKNOWN"，后续迭代与 Action 体系深度整合。
     */
    protected String getCurrentActionCode() {
        return "UNKNOWN";
    }

    /**
     * 清空已收集的领域事件。
     * <p>应用层在事件分发完成后调用，防止同一实体实例被多次操作时事件残留。
     * 正常单次工作单元模式下实体实例用完即弃，此方法的核心价值是架构约束——明确"事件已消费"的边界。</p>
     */
    public void clearDomainEvents() {
        this.eventCollector.clear();
    }

    protected void recordAction(Action action) {
        this.getActionCollector().put(action);
    }

    public boolean hasAction(Action action) {
        return this.getActionCollector().containAction(action);
    }
    public boolean hasAllActions(Action... actions) {
        return this.getActionCollector().containActions(actions);
    }


    public boolean hasAnyAction(Action... actions) {
        return this.getActionCollector().containAnyAction(actions);
    }

    private EntityActionCollector getActionCollector() {
        if (this.actionCollector == null) {
            this.actionCollector = new EntityActionCollector(this.entityActions());
        }
        return this.actionCollector;
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
        T thisId = this.getId();
        Object thatId = that.getId();
        if (thisId != null && thatId != null) {
            return thisId.equals(thatId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        T thisId = this.getId();
        return thisId != null ? thisId.hashCode() : super.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + getId() + "}";
    }
}
