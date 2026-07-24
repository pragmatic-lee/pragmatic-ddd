package io.pragmatic.ddd.base;

/**
 * 业务规则校验类异常的抽象基类。
 *
 * <p>所有通过 {@code EntityRule} 触发的校验失败异常均继承此类。
 * 框架使用者可以 {@code catch (RuleException e)} 集中处理。</p>
 *
 * @see BrokenRuleException
 * @see BrokenRuleAggregateException
 * @author lixiaojing10
 */
public abstract class RuleException extends PragmaticException {

    protected RuleException() {
        super();
    }

    protected RuleException(String message) {
        super(message);
    }

    protected RuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
