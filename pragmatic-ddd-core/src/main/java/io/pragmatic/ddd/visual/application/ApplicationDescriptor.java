package io.pragmatic.ddd.visual.application;

public class ApplicationDescriptor {
    private final String name;
    private final String applicationServiceDescription;

    private final String clsName;
    private final String methodName;

    private final String type;

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

    public String getName() {
        return name;
    }

    public String getApplicationServiceDescription() {
        return applicationServiceDescription;
    }

    public String getClsName() {
        return clsName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getType() {
        return type;
    }
}
