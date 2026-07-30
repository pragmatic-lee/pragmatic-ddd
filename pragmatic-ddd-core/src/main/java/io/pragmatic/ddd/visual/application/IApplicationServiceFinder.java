package io.pragmatic.ddd.visual.application;

import io.pragmatic.ddd.base.AbstractEntity;

import java.util.List;

/**
 * 应用服务查找器 —— 按实体类定位其应用服务方法所在类。
 *
 * @author wizard-lee
 */
public interface IApplicationServiceFinder {

   /** 返回实体类对应的应用服务方法类列表。 */
   <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls);

}
