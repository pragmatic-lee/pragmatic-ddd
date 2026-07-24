package io.pragmatic.ddd.base;

import lombok.Getter;

@Getter
public class BrokenRuleException extends RuleException {
    private final String code;
    private final String entityInfo;
    private final transient Object[] extraData;


    public BrokenRuleException(String code, String message) {
        this(code, message, "", new Object[0]);
    }

    public BrokenRuleException(String code, String message, String entityInfo, Object[] extraData) {
        super(message);
        this.code = code;
        this.extraData = extraData;
        this.entityInfo = entityInfo;
    }

    public BrokenRuleException(String code, String message, Object[] extraData) {
        this(code, message, "", extraData);
    }
}
