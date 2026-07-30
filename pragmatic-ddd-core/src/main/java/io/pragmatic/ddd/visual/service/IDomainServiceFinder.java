package io.pragmatic.ddd.visual.service;

import io.pragmatic.ddd.base.AbstractEntity;

import java.util.List;

/**
 * 领域服务查找器 —— 按实体类定位其领域服务类。
 *
 * @author wizard-lee
 */
public interface IDomainServiceFinder {
    /** 返回实体类对应的领域服务类列表。 */
    <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls);
}
