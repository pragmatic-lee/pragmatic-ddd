package io.pragmatic.ddd.mybatis.compensation;

import io.pragmatic.ddd.application.compensation.CompensationStatus;
import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.mybatis.MysqlTestSupport;
import io.pragmatic.ddd.mybatis.NoopTransactionOperations;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MySQL 版补偿日志集成测试：复用 {@link MysqlTestSupport} 连接真实 MySQL，验证
 * {@link MybatisCompensationLog} 的 WAL 写入、状态机流转、原子认领、租约回收与查询语义。
 * 无可达 MySQL 时整体跳过（构建仍成功）。
 *
 * @author wizard-lee
 */
class MybatisCompensationLogMysqlTest {

    private static SqlSessionFactory ssf;
    private SqlSession session;
    private MybatisCompensationLog log;

    @BeforeAll
    static void init() throws Exception {
        Assumptions.assumeTrue(MysqlTestSupport.isAvailable(), "MySQL 不可用，跳过集成测试");
        ssf = MysqlTestSupport.sessionFactory(CompensationMapper.class);
    }

    @BeforeEach
    void open() throws SQLException {
        session = ssf.openSession(true);   // autoCommit，测试简单起见
        try (Statement st = session.getConnection().createStatement()) {
            st.execute("DELETE FROM compensation_log");   // 跨用例隔离，避免主键冲突
        }
        log = new MybatisCompensationLog(new TestCompensationStatementExecutor(ssf), new NoopTransactionOperations());
    }

    @AfterEach
    void close() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void record_then_markExecuted_then_markConfirmed_isNeverScanned() {
        log.record(pending("k1"));
        assertThat(statusOf("k1")).isEqualTo("PENDING");

        log.markExecuted("k1");
        assertThat(statusOf("k1")).isEqualTo("EXECUTED");
        assertThat(log.findExecuted(10)).extracting(CompensationRecord::actionKey).containsExactly("k1");

        log.markConfirmed("k1");
        assertThat(statusOf("k1")).isEqualTo("CONFIRMED");
        // 提交成功的业务不得再被中继扫描到（否则会撤销已生效的外部动作）
        assertThat(log.findExecuted(10)).isEmpty();
        assertThat(log.findRetryableFailed(10, 5)).isEmpty();
    }

    @Test
    void markCompensating_claimsAtomically_onlyFirstInstanceWins() {
        log.record(pending("k1"));
        log.markExecuted("k1");

        assertThat(log.markCompensating("k1", "token-1")).isTrue();
        assertThat(log.markCompensating("k1", "token-2")).isFalse();
        assertThat(statusOf("k1")).isEqualTo("COMPENSATING");
    }

    @Test
    void markCompensated_afterClaim_movesToTerminal() {
        log.record(pending("k1"));
        log.markExecuted("k1");
        log.markCompensating("k1", "token-1");

        log.markCompensated("k1");
        assertThat(statusOf("k1")).isEqualTo("COMPENSATED");
        assertThat(log.findExecuted(10)).isEmpty();
    }

    @Test
    void markFailed_fromCompensating_accumulatesAttempts_andStaysRetryable() {
        log.record(pending("k1"));
        log.markExecuted("k1");
        log.markCompensating("k1", "token-1");

        log.markFailed("k1", "gateway timeout");
        assertThat(statusOf("k1")).isEqualTo("FAILED");
        assertThat(attemptsOf("k1")).isEqualTo(1);
        assertThat(log.findFailed(10)).extracting(CompensationRecord::actionKey).containsExactly("k1");

        // FAILED 且 attempts < maxAttempts 时可被中继重试；达上限后不再重试
        assertThat(log.findRetryableFailed(10, 2)).extracting(CompensationRecord::actionKey).containsExactly("k1");
        assertThat(log.findRetryableFailed(10, 1)).isEmpty();
    }

    @Test
    void markFailed_onConfirmed_isRejected() {
        log.record(pending("k1"));
        log.markExecuted("k1");
        log.markConfirmed("k1");

        // Confirm 失败（如 TCC Confirm 回调报错）不得把已提交记录打成可补偿状态
        log.markFailed("k1", "confirm callback failed");

        assertThat(statusOf("k1")).isEqualTo("CONFIRMED");
        assertThat(attemptsOf("k1")).isZero();
        assertThat(log.findRetryableFailed(10, 5)).isEmpty();
    }

    @Test
    void markFailed_onPendedAndExecuted_isAccepted() {
        log.record(pending("k1"));
        log.markFailed("k1", "suspended give-up");
        assertThat(statusOf("k1")).isEqualTo("FAILED");

        log.record(pending("k2"));
        log.markExecuted("k2");
        log.markFailed("k2", "forward failed");
        assertThat(statusOf("k2")).isEqualTo("FAILED");
    }

    @Test
    void releaseStaleClaims_onlyReclaimsExpiredLease() {
        log.record(pending("k1"));
        log.markExecuted("k1");
        log.markCompensating("k1", "token-1");

        // 租约 5 分钟未到期：不得回收
        assertThat(log.releaseStaleClaims(Duration.ofMinutes(5))).isZero();
        assertThat(statusOf("k1")).isEqualTo("COMPENSATING");

        backdateClaim("k1", Duration.ofMinutes(10));
        assertThat(log.releaseStaleClaims(Duration.ofMinutes(5))).isEqualTo(1);
        assertThat(statusOf("k1")).isEqualTo("EXECUTED");
    }

    @Test
    void releaseStaleClaims_ignoresNonCompensatingRecords() {
        log.record(pending("k1"));
        log.markExecuted("k1");
        log.markConfirmed("k1");

        assertThat(log.releaseStaleClaims(Duration.ZERO)).isZero();
        assertThat(statusOf("k1")).isEqualTo("CONFIRMED");
    }

    @Test
    void findSuspended_returnsPendingOnly_neverAutoCompensated() {
        // PENDING 表示正向结果未知（崩溃窗口），只供对账与人工排查
        log.record(pending("suspended-1"));
        log.record(pending("executed-1"));
        log.markExecuted("executed-1");

        assertThat(log.findSuspended(10)).extracting(CompensationRecord::actionKey).containsExactly("suspended-1");
        assertThat(log.findExecuted(10)).extracting(CompensationRecord::actionKey).containsExactly("executed-1");
    }

    @Test
    void record_persistsHandlerAndPayload_forCrossProcessRecovery() {
        log.record(new CompensationRecord(
                "k1",
                CompensationStatus.PENDING,
                "reserve-inventory",
                "{\"orderId\":\"SO-1\"}",
                0,
                Instant.now()));

        CompensationRecord loaded = log.findSuspended(10).get(0);
        assertThat(loaded.handler()).isEqualTo("reserve-inventory");
        assertThat(loaded.payload()).contains("SO-1");
    }

    @Test
    void record_duplicateActionKey_failsFast() {
        log.record(pending("k1"));

        // 当前实现为裸 INSERT：同一 actionKey 重复登记直接主键冲突（不静默覆盖）
        assertThatThrownBy(() -> log.record(pending("k1")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void updatedAt_roundTripsInstantAtMillisecondPrecision() {
        Instant written = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        log.record(new CompensationRecord("k1", CompensationStatus.PENDING, "h", "p", 0, written));

        CompensationRecord loaded = log.findSuspended(10).get(0);
        assertThat(loaded.updatedAt()).isEqualTo(written);
    }

    private CompensationRecord pending(String actionKey) {
        return new CompensationRecord(
                actionKey,
                CompensationStatus.PENDING,
                "handler-" + actionKey,
                "payload-" + actionKey,
                0,
                Instant.now());
    }

    private String statusOf(String actionKey) {
        return queryString("SELECT status FROM compensation_log WHERE action_key = ?", actionKey);
    }

    private Integer attemptsOf(String actionKey) {
        String value = queryString("SELECT attempts FROM compensation_log WHERE action_key = ?", actionKey);
        return value == null ? null : Integer.valueOf(value);
    }

    private String queryString(String sql, String param) {
        try (SqlSession s = ssf.openSession(true);
             PreparedStatement ps = s.getConnection().prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
    }

    private void backdateClaim(String actionKey, Duration age) {
        try (SqlSession s = ssf.openSession(true);
             Statement st = s.getConnection().createStatement()) {
            st.executeUpdate("UPDATE compensation_log SET claimed_at = DATE_SUB(claimed_at, INTERVAL "
                    + age.toMinutes() + " MINUTE) WHERE action_key = '" + actionKey + "'");
        } catch (SQLException e) {
            throw new IllegalStateException("backdate claim failed: " + actionKey, e);
        }
    }

    /** 基于 openSession(true) 的 ICompensationStatementExecutor 测试实现（事务由 NoopTransactionOperations 控制，不依赖 Spring）。 */
    static class TestCompensationStatementExecutor implements ICompensationStatementExecutor {

        private final SqlSessionFactory sqlSessionFactory;

        TestCompensationStatementExecutor(SqlSessionFactory sqlSessionFactory) {
            this.sqlSessionFactory = sqlSessionFactory;
        }

        @Override
        public void insert(String statementKey, CompensationRecord record) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                s.insert(statementKey, Map.of("record", record));
            }
        }

        @Override
        public void markExecuted(String statementKey, String actionKey) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                s.update(statementKey, Map.of("actionKey", actionKey));
            }
        }

        @Override
        public int markCompensating(String statementKey, String actionKey, String claimToken) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                return s.update(statementKey, Map.of("actionKey", actionKey, "claimToken", claimToken));
            }
        }

        @Override
        public void markCompensated(String statementKey, String actionKey) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                s.update(statementKey, Map.of("actionKey", actionKey));
            }
        }

        @Override
        public void markFailed(String statementKey, String actionKey, String reason) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                s.update(statementKey, Map.of("actionKey", actionKey, "reason", reason));
            }
        }

        @Override
        public void markConfirmed(String statementKey, String actionKey) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                s.update(statementKey, Map.of("actionKey", actionKey));
            }
        }

        @Override
        public List<CompensationRecord> findExecuted(String statementKey, int limit) {
            return selectList(statementKey, Map.of("limit", limit));
        }

        @Override
        public List<CompensationRecord> findSuspended(String statementKey, int limit) {
            return selectList(statementKey, Map.of("limit", limit));
        }

        @Override
        public List<CompensationRecord> findFailed(String statementKey, int limit) {
            return selectList(statementKey, Map.of("limit", limit));
        }

        @Override
        public List<CompensationRecord> findRetryableFailed(String statementKey, int limit, int maxAttempts) {
            return selectList(statementKey, Map.of("limit", limit, "maxAttempts", maxAttempts));
        }

        @Override
        public int releaseStaleClaims(String statementKey, long leaseSeconds) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                return s.update(statementKey, Map.of("leaseSeconds", leaseSeconds));
            }
        }

        private List<CompensationRecord> selectList(String statementKey, Map<String, Object> params) {
            try (SqlSession s = sqlSessionFactory.openSession(true)) {
                return s.selectList(statementKey, params);
            }
        }
    }
}
