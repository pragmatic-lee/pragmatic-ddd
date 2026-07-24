package io.pragmatic.ddd.visual.service;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.base.IDomainService;

import java.util.*;
import java.util.stream.Collectors;

public class DomainServiceParser {

    private final Map<Class<?>, IDomainServiceFinder> domainServiceMap = new HashMap<>();


    public <T extends AbstractEntity<?>> void registerDomainService(Class<T> entityClass, IDomainServiceFinder finder) {
        this.domainServiceMap.put(entityClass, finder);
    }

    public <T extends AbstractEntity<?>> List<DomainServiceDescriptor> parse(Class<T> cls) {

        List<Class<?>> domainServiceClsList = Optional.ofNullable(this.domainServiceMap.get(cls))
                .map(f->f.findList(cls)).orElse(Collections.emptyList());

        return domainServiceClsList.stream().map(s -> {

            if (Arrays.stream(s.getInterfaces()).anyMatch(inter -> inter == IDomainService.class)) {

                String description = Optional.ofNullable(s.getAnnotation(DomainServiceVisual.class))
                        .map(DomainServiceVisual::description)
                        .orElse("");

                return new DomainServiceDescriptor(s.getSimpleName(), description);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

    }
}
