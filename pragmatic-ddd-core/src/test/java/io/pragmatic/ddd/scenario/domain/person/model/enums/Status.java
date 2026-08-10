package io.pragmatic.ddd.scenario.domain.person.model.enums;

public enum Status {
    // 既有值（兼容保留）
    START,
    END,
    ILLEGAL,
    // 生命周期状态机语义
    PENDING,
    ACTIVE,
    DISABLED,
    FROZEN,
    ARCHIVED;
}
