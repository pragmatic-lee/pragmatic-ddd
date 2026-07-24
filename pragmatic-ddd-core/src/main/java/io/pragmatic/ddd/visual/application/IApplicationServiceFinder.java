package io.pragmatic.ddd.visual.application;

import io.pragmatic.ddd.base.AbstractEntity;

import java.util.List;

public interface IApplicationServiceFinder {

   <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls);

}
