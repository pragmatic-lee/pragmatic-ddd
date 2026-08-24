package io.pragmatic.ddd.application.outbox.spi;

/**
 * 最小事务抽象（core，技术无关），由基础设施模块提供实现，绑定"聚合写 + outbox 写"到同一 DB 事务。
 *
 * @author wizard-lee
 */
public interface TransactionOperations {

    /** 在事务内执行回调，提交后返回结果（默认 REQUIRED 传播行为）。 */
    <T> T execute(TransactionCallback<T> callback);

    /** 以指定传播行为在事务内执行回调，提交后返回结果。 */
    <T> T execute(TransactionCallback<T> callback, Propagation propagation);
}
