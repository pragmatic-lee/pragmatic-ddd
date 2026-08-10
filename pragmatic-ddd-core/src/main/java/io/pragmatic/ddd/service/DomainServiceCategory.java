package io.pragmatic.ddd.service;

/**
 * 领域服务分类枚举。
 *
 * @author wizard-lee
 */
public enum DomainServiceCategory {
    /** 事件订阅 */
    EVENT_SUBSCRIBER,
    /** 校验规则 */
    RULE_VALIDATOR,
    /** 属性计算（类型转换） */
    TYPE_CONVERTER,
    /** 领域工厂 / 能力供给 */
    CAPABILITY_PROVIDER,
    /** 未分类（向后兼容） */
    UNKNOWN
}
