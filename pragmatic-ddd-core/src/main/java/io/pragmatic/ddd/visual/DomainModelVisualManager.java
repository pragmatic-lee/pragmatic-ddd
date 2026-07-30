package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.visual.application.ApplicationServiceParser;
import io.pragmatic.ddd.visual.application.IApplicationServiceFinder;
import io.pragmatic.ddd.visual.entity.EntityParser;
import io.pragmatic.ddd.visual.entity.EnumValueParser;
import io.pragmatic.ddd.visual.entity.IEntityFieldFinder;
import io.pragmatic.ddd.visual.entity.IEnumValueFinder;
import io.pragmatic.ddd.visual.event.EventParser;
import io.pragmatic.ddd.visual.event.IEventFinder;
import io.pragmatic.ddd.visual.rule.IRuleFinder;
import io.pragmatic.ddd.visual.rule.RuleParser;
import io.pragmatic.ddd.visual.service.DomainServiceParser;
import io.pragmatic.ddd.visual.service.IDomainServiceFinder;

/**
 * 领域模型可视化管理器 —— 聚合各子包解析器，按实体类构建完整的可视化信息。
 *
 * @author wizard-lee
 */
public class DomainModelVisualManager {

    private final ApplicationServiceParser applicationServiceParser;
    private final EntityParser entityParser;
    private final EventParser eventParser;
    private final RuleParser ruleParser;
    private final DomainServiceParser domainServiceParser;
    private final EnumValueParser enumValueParser;

    /** 构造管理器并初始化各子包解析器。 */
    public DomainModelVisualManager(IEventManager eventManager) {
        this.applicationServiceParser = new ApplicationServiceParser();
        this.entityParser = new EntityParser();
        this.eventParser = new EventParser(eventManager);
        this.ruleParser = new RuleParser();
        this.domainServiceParser = new DomainServiceParser();
        this.enumValueParser = new EnumValueParser();
    }

    /** 注册领域实体的字段查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainEntity(Class<T> entityClass,
                                                                   IEntityFieldFinder finder) {
        this.entityParser.registerEntity(entityClass, finder);
    }

    /** 注册枚举值查找器。 */
    public <T extends AbstractEntity<?>> void registerEnum(Class<T> entityClass, IEnumValueFinder finder) {
        this.enumValueParser.registerEnum(entityClass, finder);
    }

    /** 注册应用服务查找器。 */
    public <T extends AbstractEntity<?>> void registerApplicationService(Class<T> entityClass,
                                                                         IApplicationServiceFinder finder) {
        this.applicationServiceParser.registerApplicationService(entityClass, finder);
    }

    /** 注册领域事件查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainEvent(Class<T> entityClass,
                                                                  IEventFinder finder) {
        this.eventParser.registerDomainEvent(entityClass, finder);
    }

    /** 注册领域规则查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainRule(Class<T> entityClass,
                                                                 IRuleFinder finder) {
        this.ruleParser.registerDomainRule(entityClass, finder);
    }

    /** 注册领域服务查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainService(Class<T> entityClass,
                                                                    IDomainServiceFinder finder) {
        this.domainServiceParser.registerDomainService(entityClass, finder);
    }

    /** 按实体类收集并组装全部可视化描述符。 */
    public <T extends AbstractEntity<?>> DomainModelVisualInfo build(Class<T> entityClass) {
        DomainModelVisualInfo domainModelVisualInfo = new DomainModelVisualInfo();
        domainModelVisualInfo.setEntityDescriptorList(this.entityParser.parse(entityClass));
        domainModelVisualInfo.setRuleDescriptorList(this.ruleParser.parse(entityClass));
        domainModelVisualInfo.setEventDescriptors(this.eventParser.parse(entityClass));
        domainModelVisualInfo.setDomainServiceDescriptors(this.domainServiceParser.parse(entityClass));
        domainModelVisualInfo.setEnumInfoDescriptorList(this.enumValueParser.parse(entityClass));
        domainModelVisualInfo.setApplicationDescriptors(this.applicationServiceParser.parser(entityClass));
        return domainModelVisualInfo;
    }
}
