package io.pragmatic.ddd.base;

/**
 * 业务规则校验异常的抽象基类，所有通过规则触发的校验失败异常均继承此类。
 *
 * @author wizard-lee
 */
public abstract class RuleException extends PragmaticException {

    /** 无参构造器。 */
    protected RuleException() {
        super();
    }

    /** 以消息构造。 */
    protected RuleException(String message) {
        super(message);
    }

    /** 以消息与原因构造。 */
    protected RuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
