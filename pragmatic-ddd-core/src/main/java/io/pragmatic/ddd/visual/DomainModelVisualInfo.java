package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.visual.application.ApplicationDescriptor;
import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.entity.EnumInfoDescriptor;
import io.pragmatic.ddd.visual.event.EventDescriptor;
import io.pragmatic.ddd.visual.rule.RuleDescriptorGroup;
import io.pragmatic.ddd.visual.service.DomainServiceDescriptor;

import java.util.List;

/**
 * 领域模型可视化信息聚合 —— 持有实体/规则/事件/应用服务/枚举等全部描述符列表。
 *
 * @author wizard-lee
 */
public class DomainModelVisualInfo {

    private  List<EntityDescriptor> entityDescriptorList;
    private  List<RuleDescriptorGroup> ruleDescriptorList;
    private  List<EventDescriptor> eventDescriptors;
    private  List<DomainServiceDescriptor> domainServiceDescriptors;
    private  List<EnumInfoDescriptor> enumInfoDescriptorList;
    private  List<ApplicationDescriptor> applicationDescriptors;


    /** 实体描述符列表。 */
    public List<EntityDescriptor> getEntityDescriptorList() {
        return entityDescriptorList;
    }

    /** 设置实体描述符列表。 */
    public void setEntityDescriptorList(List<EntityDescriptor> entityDescriptorList) {
        this.entityDescriptorList = entityDescriptorList;
    }

    /** 规则描述符分组列表。 */
    public List<RuleDescriptorGroup> getRuleDescriptorList() {
        return ruleDescriptorList;
    }

    /** 设置规则描述符分组列表。 */
    public void setRuleDescriptorList(List<RuleDescriptorGroup> ruleDescriptorList) {
        this.ruleDescriptorList = ruleDescriptorList;
    }

    /** 领域事件描述符列表。 */
    public List<EventDescriptor> getEventDescriptors() {
        return eventDescriptors;
    }

    /** 设置领域事件描述符列表。 */
    public void setEventDescriptors(List<EventDescriptor> eventDescriptors) {
        this.eventDescriptors = eventDescriptors;
    }

    /** 领域服务描述符列表。 */
    public List<DomainServiceDescriptor> getDomainServiceDescriptors() {
        return domainServiceDescriptors;
    }

    /** 设置领域服务描述符列表。 */
    public void setDomainServiceDescriptors(List<DomainServiceDescriptor> domainServiceDescriptors) {
        this.domainServiceDescriptors = domainServiceDescriptors;
    }

    /** 枚举信息描述符列表。 */
    public List<EnumInfoDescriptor> getEnumInfoDescriptorList() {
        return enumInfoDescriptorList;
    }

    /** 设置枚举信息描述符列表。 */
    public void setEnumInfoDescriptorList(List<EnumInfoDescriptor> enumInfoDescriptorList) {
        this.enumInfoDescriptorList = enumInfoDescriptorList;
    }

    /** 应用服务描述符列表。 */
    public List<ApplicationDescriptor> getApplicationDescriptors() {
        return applicationDescriptors;
    }

    /** 设置应用服务描述符列表。 */
    public void setApplicationDescriptors(List<ApplicationDescriptor> applicationDescriptors) {
        this.applicationDescriptors = applicationDescriptors;
    }
}
