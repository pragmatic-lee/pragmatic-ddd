package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.AbstractEntity;

import java.util.List;

/**
 * 枚举值查找器 —— 按实体类定位其枚举类型。
 *
 * @author wizard-lee
 */
public interface IEnumValueFinder {
    /** 返回实体类对应的枚举类型列表。 */
    <T extends AbstractEntity<?>> List<Class<?>> findEnums();
}
