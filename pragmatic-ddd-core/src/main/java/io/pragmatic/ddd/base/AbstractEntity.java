package io.pragmatic.ddd.base;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 实体基类 —— 纯数据容器。
 *
 * <p>对应重构计划 3.2 节：只保留身份标识、软删标记、审计字段、
 * 实体等同性（equals/hashCode）、审计时间戳工具（markCreated/markModified）
 * 与字段 CAS 工具方法（setAndReturnOld/compareAndSet）。</p>
 *
 * <p>聚合级能力（规则校验、版本号、领域事件、操作追踪、工作单元清理、新建标记）
 * 已上移至 {@link AggregateRoot}。</p>
 *
 * @param <T> 标识类型
 */
@Getter
@Setter(value = AccessLevel.PROTECTED)
public abstract class AbstractEntity<T> implements IEntity<T> {

    private T entityId;

    private boolean entityDelete;

    // ===== 审计字段 =====
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

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
