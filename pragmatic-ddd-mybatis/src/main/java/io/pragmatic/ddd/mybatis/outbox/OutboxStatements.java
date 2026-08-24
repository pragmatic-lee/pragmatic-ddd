package io.pragmatic.ddd.mybatis.outbox;

/**
 * Outbox 相关 MyBatis statement 常量（传统纯 XML 直调用）。
 * namespace 沿用原契约接口全限定名，statementId 与 OutboxMapper.xml 一一对应。
 *
 * @author wizard-lee
 */
public final class OutboxStatements {

    public static final String NAMESPACE = "io.pragmatic.ddd.mybatis.outbox.OutboxMapper";

    public static final String INSERT_BATCH = NAMESPACE + ".insertBatch";
    public static final String SELECT_BY_ID = NAMESPACE + ".selectById";
    public static final String SELECT_ATTEMPTS = NAMESPACE + ".selectAttempts";
    public static final String CLAIM = NAMESPACE + ".claim";
    public static final String SELECT_BY_CLAIM_TOKEN = NAMESPACE + ".selectByClaimToken";
    public static final String MARK_SENT = NAMESPACE + ".markSent";
    public static final String RELEASE = NAMESPACE + ".release";
    public static final String INCREMENT_ATTEMPTS = NAMESPACE + ".incrementAttempts";
    public static final String MARK_FAILED = NAMESPACE + ".markFailed";
    public static final String CLAIM_PENDING = NAMESPACE + ".claimPending";

    private OutboxStatements() {
    }
}
