package io.pragmatic.ddd.base;

import lombok.Getter;

@Getter
public class BrokenRuleException extends RuleException {
    private final String code;
    private final transient Object source;

    public BrokenRuleException(String code, String message) {
        this(code, message, null);
    }

    public BrokenRuleException(String code, String message, Object source) {
        super(message);
        this.code = code;
        this.source = source;
    }
}
