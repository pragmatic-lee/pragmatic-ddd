package io.pragmatic.ddd.operation;

/**
 * 测试用操作注册表（对应重构计划 5.1 单元测试夹具）。
 * <p>置于 operation 包以满足 OperationBoundaryArchTest 的包约束。</p>
 */
public final class SampleRegistry extends OperationRegistry {
    public static final EntityOperation A = EntityOperation.of("A");
    public static final EntityOperation B = EntityOperation.of("B");
    public static final EntityOperation C = EntityOperation.of("C");
}
