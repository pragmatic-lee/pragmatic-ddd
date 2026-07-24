package io.pragmatic.ddd.operation;

/**
 * Person 实体支持的操作集合（对应设计文档步骤 6）。
 * <p>继承 {@link OperationRegistry}，由基类反射自动注册声明的 static EntityOperation 常量，
 * 无需 registerActions() / register() 模板方法。NEW / DELETE 由基类提供。</p>
 */
public class PersonOperations extends OperationRegistry {

    public static final EntityOperation START = EntityOperation.of("startAction", "启动");
    public static final EntityOperation END = EntityOperation.of("endAction", "停止");
    public static final EntityOperation UPDATE = EntityOperation.of("updateAction", "更新");
    public static final EntityOperation UPDATE_STATUS = EntityOperation.of("updateAction", "更新状态");

    public static final PersonOperations INSTANCE = new PersonOperations();
}
