package io.pragmatic.ddd.rules;

/**
 * 规则激活状态 —— 表达一条规则在当前模型上下文中是否参与校验。
 *
 * <p>取代原有的 {@code boolean} 返回值，消除 true/false 的隐式语义映射，
 * 使调用方代码可直读。</p>
 *
 * @author wizard-lee
 */
public enum ActiveStatus {

    /** 规则生效，参与 {@link EntityRule#satisfiesRule} 的校验流程 */
    ACTIVE,

    /** 规则跳过，不参与本次校验 */
    INACTIVE
}
