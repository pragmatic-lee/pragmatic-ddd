package io.pragmatic.ddd.visual.service;

public class DomainServiceDescriptor {
    private final String clsName;
    private final String domainServiceDescription;

    public DomainServiceDescriptor(String clsName, String domainServiceDescription) {
        this.clsName = clsName;
        this.domainServiceDescription = domainServiceDescription;
    }

    public String getClsName() {
        return clsName;
    }

    public String getDomainServiceDescription() {
        return domainServiceDescription;
    }
}
