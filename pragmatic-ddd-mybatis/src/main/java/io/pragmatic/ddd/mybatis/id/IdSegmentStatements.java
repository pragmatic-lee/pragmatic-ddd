package io.pragmatic.ddd.mybatis.id;

/**
 * IdSegment 相关 MyBatis statement 常量（传统纯 XML 直调用）。
 * namespace 沿用原契约接口全限定名，statementId 与 IdSegmentMapper.xml 一一对应。
 *
 * @author wizard-lee
 */
public final class IdSegmentStatements {

    public static final String NAMESPACE = "io.pragmatic.ddd.mybatis.id.IdSegmentMapper";

    public static final String SELECT_FOR_UPDATE = NAMESPACE + ".selectForUpdate";
    public static final String INCREMENT_MAX = NAMESPACE + ".incrementMax";

    private IdSegmentStatements() {
    }
}
