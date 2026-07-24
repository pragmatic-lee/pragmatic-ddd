package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.IDomainEvent;

/**
 * 事件订阅执行状态 —— 表达某个订阅者对于给定的领域事件是否应当被执行。
 *
 * <p>取代原有的 {@code boolean} 返回值，消除 true/false 的隐式语义映射，
 * 使调用方代码可直读，并为未来扩展（如"延迟执行""跳过并记录原因"）预留空间。</p>
 */
public enum ExecuteStatus {

    /** 执行：订阅者应对该事件进行处理（等同于原 boolean 的 {@code true}） */
    EXECUTE,

    /** 跳过：订阅者不处理该事件（等同于原 boolean 的 {@code false}） */
    SKIP
}
