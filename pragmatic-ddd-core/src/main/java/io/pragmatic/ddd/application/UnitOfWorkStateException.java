package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 工作单元生命周期非法状态异常：在已提交或被 tryCommit 消费后再次调用 commit / tryCommit 时抛出。
 * 继承 {@link PragmaticException}，可通过 catch (PragmaticException) 统一兜底捕获。
 *
 * @author wizard-lee
 */
public class UnitOfWorkStateException extends PragmaticException {

    /** 以消息构造。 */
    public UnitOfWorkStateException(String message) {
        super(message);
    }
}
