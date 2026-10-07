package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 命令执行器测试专用事件管理器夹具：在累计发布条数的同时，把发布动作记录到事务夹具的动作序列中，
 * 用于断言事件发布严格发生在事务提交之后。
 */
public class RecordingEventManager extends CountingEventManager {

    /** 发布动作前缀。 */
    public static final String PUBLISH = "publish";

    private final RecordingTransactionOperations txOps;

    public RecordingEventManager(RecordingTransactionOperations txOps) {
        this.txOps = txOps;
    }

    @Override
    public <T extends IDomainEvent> void publish(T event) {
        this.txOps.record(PUBLISH);
        super.publish(event);
    }
}
