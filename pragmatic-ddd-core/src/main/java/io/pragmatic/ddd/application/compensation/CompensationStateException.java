package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 补偿范围生命周期非法状态异常：在 commit / rollback 之后再次 execute / rollback，
 * 或重复 commit / rollback 时抛出。与 UnitOfWorkStateException 同构。
 *
 * @author wizard-lee
 */
public class CompensationStateException extends PragmaticException {

    /** 以消息构造。 */
    public CompensationStateException(String message) {
        super(message);
    }
}
