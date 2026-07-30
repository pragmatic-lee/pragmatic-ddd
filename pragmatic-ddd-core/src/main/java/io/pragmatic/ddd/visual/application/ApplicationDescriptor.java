package io.pragmatic.ddd.visual.application;

/**
 * 应用服务描述符 —— 承载单个应用服务方法的名称、描述、归属类与方法信息。
 *
 * @author wizard-lee
 */
public class ApplicationDescriptor {
    private final String name;
    private final String applicationServiceDescription;

    private final String clsName;
    private final String methodName;

    private final String type;

    /** 构造应用服务描述符。 */
    public ApplicationDescriptor(String name,
                                 String applicationServiceDescription,
                                 String clsName,
                                 String methodName,
                                 String type
    ) {
        this.name = name;
        this.applicationServiceDescription = applicationServiceDescription;
        this.clsName = clsName;
        this.methodName = methodName;
        this.type = type;
    }

    /** 返回服务名称。 */
    public String getName() {
        return name;
    }

    /** 返回服务描述。 */
    public String getApplicationServiceDescription() {
        return applicationServiceDescription;
    }

    /** 返回归属类名。 */
    public String getClsName() {
        return clsName;
    }

    /** 返回方法名。 */
    public String getMethodName() {
        return methodName;
    }

    /** 返回类型（Command/Query）。 */
    public String getType() {
        return type;
    }
}
