package io.pragmatic.ddd.acl;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 防腐层（ACL）异常的抽象基类，归类所有防腐层调用相关的异常。
 * 继承 {@link PragmaticException}，可通过 catch (PragmaticException) 统一兜底。
 *
 * @author wizard-lee
 */
public abstract class AclException extends PragmaticException {

    protected AclException() {
        super();
    }

    protected AclException(String message) {
        super(message);
    }

    protected AclException(String message, Throwable cause) {
        super(message, cause);
    }

    protected AclException(Throwable cause) {
        super(cause);
    }
}
