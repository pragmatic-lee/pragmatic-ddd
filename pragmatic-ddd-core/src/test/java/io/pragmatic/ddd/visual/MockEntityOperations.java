package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * MockEntity 支持的操作集合（对应设计文档步骤 6）。
 * <p>继承 {@link OperationRegistry}，由基类反射自动注册声明的 static EntityOperation 常量。</p>
 *
 * @author wizard-lee
 */
public class MockEntityOperations extends OperationRegistry {

    public static final EntityOperation showNameAction = EntityOperation.of("showNameAction", "测试Action");

    public static final MockEntityOperations INSTANCE = new MockEntityOperations();
}
