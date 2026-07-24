package io.pragmatic.ddd.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author lixiaojing10

 */
public class DomainEventCollector {

    private final List<IDomainEvent> events = new ArrayList<>();

    private final List<Supplier<IDomainEvent>> delayedEvents = new ArrayList<>();

    public void collect(IDomainEvent domainEvent) {
        this.events.add(domainEvent);
    }

    public void collectDelayed(Supplier<IDomainEvent> supplier) {
        this.delayedEvents.add(supplier);
    }

    public List<IDomainEvent> getEvents() {

        List<IDomainEvent> delayDomainEventList = this.delayedEvents
                .stream()
                .map(Supplier::get)
                .toList();

        List<IDomainEvent> returnedList = new ArrayList<>(this.events);
        returnedList.addAll(delayDomainEventList);

        return returnedList;
    }

    public <T> void removeEvent(Class<T> cls) {
        events.removeIf(s -> s.getClass().equals(cls));
    }

    public void clear() {
        this.events.clear();
        this.delayedEvents.clear();
    }
}
