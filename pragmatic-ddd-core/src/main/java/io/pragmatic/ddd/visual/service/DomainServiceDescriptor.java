package io.pragmatic.ddd.visual.service;

/**
 * 领域服务描述符 —— 承载单个领域服务类的名称与描述。
 *
 * @author wizard-lee
 */
public class DomainServiceDescriptor {
    private final String clsName;
    private final String domainServiceDescription;

    /** 构造领域服务描述符。 */
    public DomainServiceDescriptor(String clsName, String domainServiceDescription) {
        this.clsName = clsName;
        this.domainServiceDescription = domainServiceDescription;
    }

    /** 返回领域服务类名。 */
    public String getClsName() {
        return clsName;
    }

    /** 返回领域服务描述。 */
    public String getDomainServiceDescription() {
        return domainServiceDescription;
    }
}
