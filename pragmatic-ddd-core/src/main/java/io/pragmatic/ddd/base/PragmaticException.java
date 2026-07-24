package io.pragmatic.ddd.base;

/**
 * 框架所有业务异常的抽象基类。
 *
 * <p>命名遵循 {@code HibernateException} / {@code JacksonException} 的惯例：
 * 使用框架品牌名，让 stack trace 中来源一目了然。</p>
 *
 * <p>框架使用者可以通过 {@code catch (PragmaticException e)} 统一捕获所有框架层异常：</p>
 * <pre>{@code
 * try {
 *     aggregateRoot.someBusinessOperation();
 * } catch (RuleException e) {
 *     // 精确：业务规则校验失败
 * } catch (PragmaticException e) {
 *     // 兜底：所有其他框架异常
 * }
 * }</pre>
 *
 * @author lixiaojing10
 * @since 2.1.0
 */
public abstract class PragmaticException extends RuntimeException {

    protected PragmaticException() {
        super();
    }

    protected PragmaticException(String message) {
        super(message);
    }

    protected PragmaticException(String message, Throwable cause) {
        super(message, cause);
    }

    protected PragmaticException(Throwable cause) {
        super(cause);
    }
}
