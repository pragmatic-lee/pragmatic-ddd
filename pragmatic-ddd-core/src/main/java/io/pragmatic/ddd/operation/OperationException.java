package io.pragmatic.ddd.operation;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 实体操作相关异常（对应设计文档步骤 4：替代原 {@code action.ActionException}）。
 */
public class OperationException extends PragmaticException {

    public OperationException(String message) {
        super(message);
    }
}
