package io.pragmatic.ddd.mybatis.compensation;

/**
 * 补偿日志相关 MyBatis statement 常量（传统纯 XML 直调用）。
 * namespace 沿用契约接口全限定名，statementId 与 CompensationMapper.xml 一一对应。
 *
 * @author wizard-lee
 */
public final class CompensationStatements {

    public static final String NAMESPACE = "io.pragmatic.ddd.mybatis.compensation.CompensationMapper";

    public static final String INSERT = NAMESPACE + ".insert";
    public static final String MARK_EXECUTED = NAMESPACE + ".markExecuted";
    public static final String MARK_COMPENSATING = NAMESPACE + ".markCompensating";
    public static final String MARK_COMPENSATED = NAMESPACE + ".markCompensated";
    public static final String MARK_FAILED = NAMESPACE + ".markFailed";
    public static final String FIND_EXECUTED = NAMESPACE + ".findExecuted";
    public static final String FIND_SUSPENDED = NAMESPACE + ".findSuspended";
    public static final String FIND_FAILED = NAMESPACE + ".findFailed";

    private CompensationStatements() {
    }
}
