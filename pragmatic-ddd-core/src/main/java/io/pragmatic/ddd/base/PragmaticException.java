package io.pragmatic.ddd.base;

/**
 * 框架所有业务异常的抽象基类。
 * 命名遵循 HibernateException / JacksonException 惯例，便于在 stack trace 中识别来源；
 * 可通过 catch (PragmaticException e) 统一兜底捕获所有框架异常。
 *
 * @author wizard-lee
 */
public abstract class PragmaticException extends RuntimeException {

    /** 无参构造器。 */
    protected PragmaticException() {
        super();
    }

    /** 以消息构造。 */
    protected PragmaticException(String message) {
        super(message);
    }

    /** 以消息与原因构造。 */
    protected PragmaticException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 以原因构造。 */
    protected PragmaticException(Throwable cause) {
        super(cause);
    }
}
