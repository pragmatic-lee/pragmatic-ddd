package io.pragmatic.ddd.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 实体已触发领域事件收集器（对应 {@code TriggeredOperations} 的事件侧对等物）。
 *
 * <p>持有两类事件：</p>
 * <ul>
 *   <li><b>即时事件</b>：通过 {@link #collect(IDomainEvent)} 直接加入；</li>
 *   <li><b>延迟事件</b>：通过 {@link #collectDelayed(Supplier)} 以 Supplier 形式登记，
 *       在 {@link #getEvents()} / {@link #drain()} 被调用时才惰性求值，
 *       用于捕获 flush 时刻的最新实体状态。</li>
 * </ul>
 *
 * <p>延迟事件只会被求值一次，求值后即并入即时列表，保证多次读取的幂等性。
 * 本类假设被单个实体在单线程环境下使用，非线程安全。</p>
 */
public class TriggeredEvents {

    private final List<IDomainEvent> events = new ArrayList<>();

    private final List<Supplier<IDomainEvent>> deferredEvents = new ArrayList<>();

    /**
     * 收集一个即时领域事件，立即加入即时事件列表。
     *
     * @param event 待触发的领域事件
     */
    public void collect(IDomainEvent event) {
        this.events.add(event);
    }

    /**
     * 以 Supplier 形式登记一个延迟事件，在 {@link #getEvents()} / {@link #drain()} 时才惰性求值，
     * 用于捕获 flush 时刻的最新实体状态。
     *
     * @param supplier 延迟事件的构造器
     */
    public void collectDelayed(Supplier<IDomainEvent> supplier) {
        this.deferredEvents.add(supplier);
    }

    /**
     * 返回当前全部事件的不可变快照。
     * 调用时会先惰性求值所有延迟事件（仅一次），再以 {@code List.copyOf} 返回不可变列表，
     * 根治延迟事件每次读取产生新实例的非幂等问题。
     *
     * @return 不可变事件列表快照
     */
    public List<IDomainEvent> getEvents() {
        materializeDeferred();
        return List.copyOf(this.events);
    }

    /**
     * 原子取空：先惰性求值延迟事件，返回全部事件的不可变快照，并清空所有内部状态。
     * 适用于“取走并清空”的一次性消费场景（如 outbox 派发）。
     *
     * @return 取空前全部事件的不可变快照
     */
    public List<IDomainEvent> drain() {
        materializeDeferred();
        List<IDomainEvent> snapshot = List.copyOf(this.events);
        this.clear();
        return snapshot;
    }

    /**
     * 按类型（含子类型）移除所有匹配的事件；调用前会先惰性求值延迟事件，因此延迟事件同样可被移除。
     *
     * @param eventType 待移除事件的类型（使用 {@code isAssignableFrom} 匹配，可移除其子类实例）
     */
    public void removeEvent(Class<? extends IDomainEvent> eventType) {
        materializeDeferred();
        this.events.removeIf(e -> eventType.isAssignableFrom(e.getClass()));
    }

    /**
     * 清空即时事件与延迟事件登记。
     */
    public void clear() {
        this.events.clear();
        this.deferredEvents.clear();
    }

    /**
     * 惰性求值延迟事件：将所有登记的 Supplier 求值一次并并入即时事件列表，随后清空延迟列表，
     * 保证延迟事件在整个生命周期内只被物化一次。
     */
    private void materializeDeferred() {
        if (this.deferredEvents.isEmpty()) {
            return;
        }
        for (Supplier<IDomainEvent> supplier : this.deferredEvents) {
            this.events.add(supplier.get());
        }
        this.deferredEvents.clear();
    }
}
