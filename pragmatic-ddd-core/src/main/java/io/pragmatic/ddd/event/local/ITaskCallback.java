package io.pragmatic.ddd.event.local;

/**
 * 任务执行回调，事件处理完成后触发，用于驱动后续订阅者传播。
 *
 * @author wizard-lee
 */
public interface ITaskCallback {

    /** 事件被对应订阅者处理完成后调用。 */
    void execute(Task task);
}
