package io.pragmatic.ddd.repository.query.exception;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 读侧投影检索域的异常抽象基类，归类所有检索相关的异常。
 * 继承 {@link PragmaticException}，可通过 catch (PragmaticException) 统一兜底。
 *
 * @author wizard-lee
 */
public abstract class ProjectionException extends PragmaticException {

    protected ProjectionException() {
        super();
    }

    protected ProjectionException(String message) {
        super(message);
    }

    protected ProjectionException(String message, Throwable cause) {
        super(message, cause);
    }

    protected ProjectionException(Throwable cause) {
        super(cause);
    }
}
