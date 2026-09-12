package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 副本重复登记异常：同一 {@link io.pragmatic.ddd.repository.ReplicaKey} 被两个不同实例登记时抛出。
 *
 * @author wizard-lee
 */
public class ReconcileDuplicateReplicaException extends PragmaticException {

    /** 以消息构造。 */
    public ReconcileDuplicateReplicaException(String message) {
        super(message);
    }
}
