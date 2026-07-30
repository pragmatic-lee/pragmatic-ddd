package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * EntityTest2 支持的操作集合（对应设计文档步骤 6）。
 * <p>继承 {@link OperationRegistry}，由基类反射自动注册声明的 static EntityOperation 常量。</p>
 *
 * @author wizard-lee
 */
public class EntityTest2Operations extends OperationRegistry {

    public static EntityOperation TEST = EntityOperation.of("TestAction");
}
