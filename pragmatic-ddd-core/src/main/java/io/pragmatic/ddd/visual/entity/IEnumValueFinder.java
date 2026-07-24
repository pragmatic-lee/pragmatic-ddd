package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.AbstractEntity;

import java.util.List;

public interface IEnumValueFinder {
    <T extends AbstractEntity<?>> List<Class<?>> findEnums();
}
