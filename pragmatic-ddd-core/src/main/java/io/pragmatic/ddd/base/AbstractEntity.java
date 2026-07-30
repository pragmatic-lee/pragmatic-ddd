package io.pragmatic.ddd.base;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 实体基类，纯数据容器：承载身份标识、软删标记、审计字段，
 * 以及基于身份标识的实体等同性（equals/hashCode）与审计时间戳工具（markCreated/markModified）。
 * 聚合级能力（规则校验、版本号、领域事件、操作追踪、工作单元清理、新建标记）由 {@link AggregateRoot} 承载。
 *
 * @param <T> 标识类型
 * @author wizard-lee
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


    // ============ 审计时间戳 ============

    /** 标记实体已创建：设置 createdAt 与 updatedAt 为当前时间，子类在构造末尾调用一次。 */
    protected void markCreated() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 标记实体已被修改：更新 updatedAt 为当前时间。 */
    protected void markModified() {
        this.updatedAt = LocalDateTime.now();
    }

    // ============ 实体等同性（基于身份标识 ID） ============

    /** 基于身份标识判断等同性：两实体 ID 均非空且相等才视为同一实体。 */
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

    /** 以身份标识计算哈希；ID 为空时退回父类哈希。 */
    @Override
    public int hashCode() {
        T thisId = this.getEntityId();
        return thisId != null ? thisId.hashCode() : super.hashCode();
    }

    /** 返回"类名{id=...}"形式。 */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + getEntityId() + "}";
    }
}
