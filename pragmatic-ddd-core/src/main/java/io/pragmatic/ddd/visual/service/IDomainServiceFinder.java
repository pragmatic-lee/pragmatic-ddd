package io.pragmatic.ddd.visual.service;

import io.pragmatic.ddd.base.AbstractEntity;

import java.util.List;

public interface IDomainServiceFinder {
    <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls);
}
