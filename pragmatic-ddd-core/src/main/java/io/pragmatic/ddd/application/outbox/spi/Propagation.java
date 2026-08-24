package io.pragmatic.ddd.application.outbox.spi;

/**
 * 事务传播行为（core，技术无关）。
 * REQUIRED 加入现有事务，无则新建（聚合写 + outbox 写同事务）；
 * REQUIRES_NEW 挂起现有事务并新建独立事务（补偿操作独立短事务）。
 *
 * @author wizard-lee
 */
public enum Propagation {

    REQUIRED,
    REQUIRES_NEW
}
