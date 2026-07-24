package io.pragmatic.ddd.base;

import java.util.List;

public class BrokenRuleAggregateException extends RuleException {

    private final List<BrokenRuleException> exceptions;

    public BrokenRuleAggregateException(String message, List<BrokenRuleException> exceptions) {
        super(message);
        this.exceptions = exceptions;
    }

    public BrokenRuleAggregateException(List<BrokenRuleException> exceptions) {
        this("", exceptions);
    }

    public List<BrokenRuleException> getExceptions() {
        return exceptions;
    }
}
