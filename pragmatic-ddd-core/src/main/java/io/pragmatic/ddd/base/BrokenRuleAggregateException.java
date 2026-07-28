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

    /** 便捷：返回首个子异常的 source（聚合场景下所有子异常 source 相同） */
    public Object getSource() {
        return exceptions.isEmpty() ? null : exceptions.get(0).getSource();
    }
}
