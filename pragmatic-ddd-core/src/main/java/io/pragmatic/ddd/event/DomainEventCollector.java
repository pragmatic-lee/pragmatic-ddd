package io.pragmatic.ddd.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * @author lixiaojing10

 */
public class DomainEventCollector {

    private final List<IDomainEvent> domainEventList = new ArrayList<>();

    private final List<Supplier<IDomainEvent>> delayGenerateEventList = new ArrayList<>();

    public void pushEvent(IDomainEvent domainEvent) {
        this.domainEventList.add(domainEvent);
    }

    public void pushDelayGenerateEvent(Supplier<IDomainEvent> supplier) {
        this.delayGenerateEventList.add(supplier);
    }

    public List<IDomainEvent> getEventList() {

        List<IDomainEvent> delayDomainEventList = this.delayGenerateEventList
                .stream()
                .map(Supplier::get)
                .toList();

        List<IDomainEvent> returnedList = new ArrayList<>(this.domainEventList);
        returnedList.addAll(delayDomainEventList);

        return returnedList;
    }

    public <T> void removeEvent(Class<T> cls) {
        domainEventList.removeIf(s -> s.getClass().equals(cls));
    }

    public void clear() {
        this.domainEventList.clear();
        this.delayGenerateEventList.clear();
    }
}
