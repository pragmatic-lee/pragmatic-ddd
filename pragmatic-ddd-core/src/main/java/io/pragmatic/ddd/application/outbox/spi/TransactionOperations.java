package io.pragmatic.ddd.application.outbox.spi;

/**
 * 最小事务抽象（core，技术无关）。由基础设施模块（如 Spring）提供实现，
 * 绑定"聚合写 + outbox 写"到同一 DB 事务。
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public interface TransactionOperations {

    /**
     * 在事务内执行回调，提交后返回结果。
     *
     * @param callback 事务内逻辑
     * @param <T>      返回值类型
     * @return 回调返回值
     */
    <T> T execute(TransactionCallback<T> callback);
}
