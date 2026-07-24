package io.pragmatic.ddd.operation;

/**
 * EntityTest2 支持的操作集合（对应设计文档步骤 6）。
 * <p>继承 {@link OperationRegistry}，由基类反射自动注册声明的 static EntityOperation 常量。</p>
 */
public class EntityTest2Action extends OperationRegistry {

    public static EntityOperation testAction = EntityOperation.of("TestAction");
}
