package io.pragmatic.ddd.rules;

/**
 * 规则生命周期契约 —— 提供初始化与重置钩子。
 *
 * @author wizard-lee
 */
public interface IRuleBuild {
    /** 初始化规则（由子类填充规则项）。 */
    default void init(){}

    /** 重置规则至初始状态。 */
    void reset();
}
