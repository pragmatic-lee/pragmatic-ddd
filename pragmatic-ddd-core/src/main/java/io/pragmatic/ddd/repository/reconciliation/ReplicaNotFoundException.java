package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 副本未找到异常：按 {@link io.pragmatic.ddd.repository.ReplicaKey} 取副本时未登记抛出。
 *
 * @author wizard-lee
 */
public class ReplicaNotFoundException extends PragmaticException {

    /** 以消息构造。 */
    public ReplicaNotFoundException(String message) {
        super(message);
    }
}
