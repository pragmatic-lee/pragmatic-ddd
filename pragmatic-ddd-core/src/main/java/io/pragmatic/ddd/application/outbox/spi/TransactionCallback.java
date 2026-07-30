package io.pragmatic.ddd.application.outbox.spi;

/**
 * 事务回调（core，技术无关），封装事务内执行的返回值逻辑。
 *
 * @author wizard-lee
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction();
}
