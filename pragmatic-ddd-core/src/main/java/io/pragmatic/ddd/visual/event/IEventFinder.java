package io.pragmatic.ddd.visual.event;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.base.AbstractEntity;
import java.util.List;

/**
 * 领域事件查找器 —— 按实体类定位其领域事件类与订阅键信息。
 *
 * @author wizard-lee
 */
public interface IEventFinder {

    /** 返回实体类对应的领域事件类列表。 */
    <T extends AbstractEntity<?>> List<Class<?>> findersList(Class<T> cls);

    /** 返回订阅键信息提取器。 */
    AbstractSubscriberKey eventSubscribeKey();
}
