package io.pragmatic.ddd.base;

import java.util.List;

/**
 * 聚合异常：多条规则违反时聚合抛出，持有所有子异常（BrokenRuleException）。
 *
 * @author wizard-lee
 */
public class BrokenRuleAggregateException extends RuleException {

    private final List<BrokenRuleException> exceptions;

    /** 以消息与子异常列表创建聚合异常。 */
    public BrokenRuleAggregateException(String message, List<BrokenRuleException> exceptions) {
        super(message);
        this.exceptions = exceptions;
    }

    /** 以空消息与子异常列表创建聚合异常。 */
    public BrokenRuleAggregateException(List<BrokenRuleException> exceptions) {
        this("", exceptions);
    }

    /** 返回全部子异常。 */
    public List<BrokenRuleException> getExceptions() {
        return exceptions;
    }

    /** 返回首个子异常的 source（聚合场景下所有子异常 source 相同）。 */
    public Object getSource() {
        return exceptions.isEmpty() ? null : exceptions.get(0).getSource();
    }
}
