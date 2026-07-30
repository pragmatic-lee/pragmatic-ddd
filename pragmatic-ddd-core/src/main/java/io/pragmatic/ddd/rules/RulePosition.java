package io.pragmatic.ddd.rules;

/**
 * 规则插入位置 — 表达 appendRule 中规则相对于参照规则的插入位置。
 *
 * <p>取代原有的 int 魔术数字（0=last, 1=before, 2=after），
 * 使调用方代码可直读。</p>
 *
 * @author wizard-lee
 */
public enum RulePosition {

    /** 追加到规则列表末尾（不依赖参照规则） */
    LAST,

    /** 插入到参照规则之前 */
    BEFORE,

    /** 插入到参照规则之后 */
    AFTER
}
