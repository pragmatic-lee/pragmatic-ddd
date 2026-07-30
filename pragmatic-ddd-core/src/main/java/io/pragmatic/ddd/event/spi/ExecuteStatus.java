package io.pragmatic.ddd.event.spi;



/**
 * 事件订阅执行状态：表达某订阅者对于给定的领域事件是否应当被执行。
 *
 * @author wizard-lee
 */
public enum ExecuteStatus {

    /** 执行：订阅者应对该事件进行处理（等同于原 boolean 的 {@code true}） */
    EXECUTE,

    /** 跳过：订阅者不处理该事件（等同于原 boolean 的 {@code false}） */
    SKIP
}
