package io.pragmatic.ddd.springboot.bridge;

import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionCallback;
import io.pragmatic.ddd.application.spi.TransactionOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring {@link TransactionTemplate} 的 {@link TransactionOperations} 实现。
 * 桥接框架的事务回调与 Spring 事务回调，把聚合写与 outbox 写绑定到同一本地事务，
 * 补偿类操作则以独立短事务（REQUIRES_NEW）立即提交。
 *
 * <p>两种传播行为对应的模板在构造期一次性建好并缓存，避免热路径上重复构造模板。</p>
 *
 * @author wizard-lee
 */
public class SpringTransactionOperations implements TransactionOperations {

    private final TransactionTemplate required;
    private final TransactionTemplate requiresNew;

    /**
     * 以事务管理器构建桥接器，预建 REQUIRED 与 REQUIRES_NEW 两个模板。
     *
     * @param transactionManager Spring 事务管理器（通常由官方 JDBC 自动配置提供）
     */
    public SpringTransactionOperations(PlatformTransactionManager transactionManager) {
        this.required = new TransactionTemplate(transactionManager);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 以默认 REQUIRED 传播行为在事务内执行回调。
     *
     * @param callback 事务内执行的回调
     * @param <T>      返回值类型
     * @return 回调结果
     */
    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return this.required.execute(status -> callback.doInTransaction());
    }

    /**
     * 以指定传播行为在事务内执行回调。
     *
     * @param callback    事务内执行的回调
     * @param propagation 传播行为（REQUIRED / REQUIRES_NEW）
     * @param <T>         返回值类型
     * @return 回调结果
     */
    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        TransactionTemplate template = switch (propagation) {
            case REQUIRES_NEW -> this.requiresNew;
            case REQUIRED -> this.required;
        };
        return template.execute(status -> callback.doInTransaction());
    }
}
