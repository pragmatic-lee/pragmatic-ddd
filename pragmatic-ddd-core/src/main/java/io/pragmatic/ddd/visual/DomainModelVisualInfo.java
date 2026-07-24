package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.visual.application.ApplicationDescriptor;
import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.entity.EnumInfoDescriptor;
import io.pragmatic.ddd.visual.event.EventDescriptor;
import io.pragmatic.ddd.visual.rule.RuleDescriptorGroup;
import io.pragmatic.ddd.visual.service.DomainServiceDescriptor;

import java.util.List;

public class DomainModelVisualInfo {

    private  List<EntityDescriptor> entityDescriptorList;
    private  List<RuleDescriptorGroup> ruleDescriptorList;
    private  List<EventDescriptor> eventDescriptors;
    private  List<DomainServiceDescriptor> domainServiceDescriptors;
    private  List<EnumInfoDescriptor> enumInfoDescriptorList;
    private  List<ApplicationDescriptor> applicationDescriptors;


    public List<EntityDescriptor> getEntityDescriptorList() {
        return entityDescriptorList;
    }

    public void setEntityDescriptorList(List<EntityDescriptor> entityDescriptorList) {
        this.entityDescriptorList = entityDescriptorList;
    }

    public List<RuleDescriptorGroup> getRuleDescriptorList() {
        return ruleDescriptorList;
    }

    public void setRuleDescriptorList(List<RuleDescriptorGroup> ruleDescriptorList) {
        this.ruleDescriptorList = ruleDescriptorList;
    }

    public List<EventDescriptor> getEventDescriptors() {
        return eventDescriptors;
    }

    public void setEventDescriptors(List<EventDescriptor> eventDescriptors) {
        this.eventDescriptors = eventDescriptors;
    }

    public List<DomainServiceDescriptor> getDomainServiceDescriptors() {
        return domainServiceDescriptors;
    }

    public void setDomainServiceDescriptors(List<DomainServiceDescriptor> domainServiceDescriptors) {
        this.domainServiceDescriptors = domainServiceDescriptors;
    }

    public List<EnumInfoDescriptor> getEnumInfoDescriptorList() {
        return enumInfoDescriptorList;
    }

    public void setEnumInfoDescriptorList(List<EnumInfoDescriptor> enumInfoDescriptorList) {
        this.enumInfoDescriptorList = enumInfoDescriptorList;
    }

    public List<ApplicationDescriptor> getApplicationDescriptors() {
        return applicationDescriptors;
    }

    public void setApplicationDescriptors(List<ApplicationDescriptor> applicationDescriptors) {
        this.applicationDescriptors = applicationDescriptors;
    }
}
