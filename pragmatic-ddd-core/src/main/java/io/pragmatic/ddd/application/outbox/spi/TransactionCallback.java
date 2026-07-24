package io.pragmatic.ddd.application.outbox.spi;

/**
 * 事务回调（core，技术无关）。
 *
 * @param <T> 返回值类型
 * @author Li XiaoJing
 * @since 2.2.0
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction();
}
