package io.pragmatic.ddd.operation;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 实体操作相关异常（对应设计文档步骤 4：替代原 {@code action.ActionException}）。
 *
 * @author wizard-lee
 */
public class OperationException extends PragmaticException {

    /** 构造实体操作异常。 */
    public OperationException(String message) {
        super(message);
    }
}
