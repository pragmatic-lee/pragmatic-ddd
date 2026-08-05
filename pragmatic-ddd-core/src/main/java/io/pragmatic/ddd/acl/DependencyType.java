package io.pragmatic.ddd.acl;

/**
 * 外部依赖类型。
 *
 * @author wizard-lee
 */
public enum DependencyType {

    /** 外部聚合：同一系统内的其他聚合根。 */
    AGGREGATE,

    /** 外部系统：系统边界之外的服务、第三方 API。 */
    EXTERNAL_SYSTEM
}
