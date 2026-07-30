package io.pragmatic.ddd.base;

import lombok.Getter;

/**
 * 单条规则违反异常，继承 {@link RuleException}，携带 code、消息与触发源。
 *
 * @author wizard-lee
 */
@Getter
public class BrokenRuleException extends RuleException {
    private final String code;
    private final transient Object source;

    /** 创建不含触发源的规则违反异常。 */
    public BrokenRuleException(String code, String message) {
        this(code, message, null);
    }

    /** 创建含触发源的规则违反异常。 */
    public BrokenRuleException(String code, String message, Object source) {
        super(message);
        this.code = code;
        this.source = source;
    }
}
