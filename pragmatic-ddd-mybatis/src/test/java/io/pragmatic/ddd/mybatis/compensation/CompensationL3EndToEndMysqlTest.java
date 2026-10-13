package io.pragmatic.ddd.mybatis.compensation;

import io.pragmatic.ddd.application.compensation.CompensationCommand;
import io.pragmatic.ddd.application.compensation.CompensationOptions;
import io.pragmatic.ddd.application.compensation.CompensationStatus;
import io.pragmatic.ddd.application.compensation.CompensationTemplate;
import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.application.compensation.ICompensationManager;
import io.pragmatic.ddd.application.compensation.LocalCompensationManager;
import io.pragmatic.ddd.application.compensation.relay.CompensationRelay;
import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.mybatis.MysqlTestSupport;
import io.pragmatic.ddd.mybatis.NoopTransactionOperations;
import io.pragmatic.ddd.operation.OperationRegistry;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿 L3（durable + MyBatis WAL + 中继）端到端集成测试：连接真实 MySQL，验证
 * 成功路径落 CONFIRMED、失败路径逆序补偿、悬挂记录不被自动补偿、崩溃残留由中继补完。
 * 无可达 MySQL 时整体跳过（构建仍成功）。
 *
 * @author wizard-lee
 */
class CompensationL3EndToEndMysqlTest {

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
        session = ssf.openSession(true);
        try (Statement st = session.getConnection().createStatement()) {
            st.execute("DELETE FROM compensation_log");
        }
        log = new MybatisCompensationLog(new TestStatementExecutor(ssf), new NoopTransactionOperations());
    }

    @AfterEach
    void close() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void successPath_allConfirmed_andRelayDoesNotCompensate() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager(log, CompensationOptions.durableMode());

        CompensationTemplate.run(manager, scope -> {
            scope.execute(command(new L3Action("a1", "p1", journal)));
            scope.execute(command(new L3Action("a2", "p2", journal)));
        });

        assertThat(journal).containsExactly("execute:a1", "execute:a2");
        assertThat(statusOf(key("a1"))).isEqualTo("CONFIRMED");
        assertThat(statusOf(key("a2"))).isEqualTo("CONFIRMED");

        // 中继扫描不到任何待补偿记录：成功业务不会被误撤销
        new CompensationRelay(log, List.of(
                new RecordingHandler("a1", journal),
                new RecordingHandler("a2", journal))).pollOnce();
        assertThat(journal).containsExactly("execute:a1", "execute:a2");
    }

    @Test
    void midwayFailure_reverseCompensatesExecuted_andFailedForwardStaysSuspended() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager(log, CompensationOptions.durableMode());

        assertThatThrownBy(() -> CompensationTemplate.run(manager, scope -> {
            scope.execute(command(new L3Action("a1", "p1", journal)));
            scope.execute(command(new L3Action("a2", "p2", journal).failOnExecute()));
        })).isInstanceOf(IllegalStateException.class);

        // 所见即所偿：a2 正向失败未登记（WAL 留在 PENDING 悬挂），只逆序补偿 a1
        assertThat(journal).containsExactly("execute:a1", "compensate:a1");
        assertThat(statusOf(key("a1"))).isEqualTo("COMPENSATED");
        assertThat(statusOf(key("a2"))).isEqualTo("PENDING");
        assertThat(log.findSuspended(10)).extracting(CompensationRecord::actionKey).containsExactly(key("a2"));
    }

    @Test
    void relay_compensatesResidualExecutedRecords_afterCrash() {
        List<String> journal = new ArrayList<>();
        // 模拟崩溃残留：正向已成功（EXECUTED），但进程在补偿前挂掉
        log.record(pending(key("a1"), "a1", "p1"));
        log.markExecuted(key("a1"));

        new CompensationRelay(log, List.of(new RecordingHandler("a1", journal))).pollOnce();

        assertThat(journal).containsExactly("relay:a1:p1");
        assertThat(statusOf(key("a1"))).isEqualTo("COMPENSATED");
    }

    @Test
    void relay_withoutHandler_marksFailedWithDiagnosableReason() {
        List<String> journal = new ArrayList<>();
        log.record(pending(key("a1"), "unknown-action", "p1"));
        log.markExecuted(key("a1"));

        new CompensationRelay(log, List.of()).pollOnce();

        assertThat(statusOf(key("a1"))).isEqualTo("FAILED");
        assertThat(lastErrorOf(key("a1"))).contains("no handler for unknown-action");
        assertThat(journal).isEmpty();
    }

    @Test
    void relay_retriesFailedRecord_untilSuccess() {
        List<String> journal = new ArrayList<>();
        RecordingHandler handler = new RecordingHandler("a1", journal).failingFor(1);
        log.record(pending(key("a1"), "a1", "p1"));
        log.markExecuted(key("a1"));

        CompensationRelay relay = new CompensationRelay(log, List.of(handler));
        relay.pollOnce();
        assertThat(statusOf(key("a1"))).isEqualTo("FAILED");
        assertThat(attemptsOf(key("a1"))).isEqualTo(1);

        relay.pollOnce();
        assertThat(statusOf(key("a1"))).isEqualTo("COMPENSATED");
        assertThat(journal).containsExactly("relay:a1:p1", "relay:a1:p1");
    }

    @Test
    void durable_emptyPayload_failsFast_beforeAnyRowWritten() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager(log, CompensationOptions.durableMode());
        L3Action noPayload = new L3Action("a1", "", journal);

        assertThatThrownBy(() -> CompensationTemplate.run(manager, scope -> {
            scope.execute(command(noPayload));
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-empty payload");

        assertThat(journal).isEmpty();
        assertThat(countRows()).isZero();
    }

    private String key(String code) {
        return "L3Aggregate:1:" + code;
    }

    private CompensationCommand<L3Aggregate, L3Requirement, String> command(L3Action action) {
        return new CompensationCommand<>(action, new L3Aggregate(), action.requirement());
    }

    private CompensationRecord pending(String actionKey, String handler, String payload) {
        return new CompensationRecord(
                actionKey,
                CompensationStatus.PENDING,
                handler,
                payload,
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

    private String lastErrorOf(String actionKey) {
        return queryString("SELECT last_error FROM compensation_log WHERE action_key = ?", actionKey);
    }

    private int countRows() {
        String value = queryString("SELECT COUNT(*) FROM compensation_log WHERE action_key IS NOT NULL", "");
        return value == null ? 0 : Integer.parseInt(value);
    }

    private String queryString(String sql, String param) {
        try (SqlSession s = ssf.openSession(true);
             PreparedStatement ps = s.getConnection().prepareStatement(sql)) {
            if (!param.isEmpty()) {
                ps.setString(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
    }

    /** 测试用外部需求：仅携带需求编码。 */
    private record L3Requirement(String code) implements IExternalRequirement {
    }

    /** 测试用聚合根桩：固定标识 1，使 actionKey 形如 {@code L3Aggregate:1:code}。 */
    private static final class L3Aggregate extends AggregateRoot<Long> {

        L3Aggregate() {
            this.setEntityId(1L);
        }

        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return null;
        }

        @Override
        protected OperationRegistry operationRegistry() {
            return null;
        }
    }

    /** 测试用可补偿动作：正向 / 逆向写入共享 journal，可配置 payload 与正向失败。 */
    private static final class L3Action implements ICompensableAction<L3Aggregate, L3Requirement, String> {

        private final L3Requirement requirement;
        private final String payload;
        private final List<String> journal;
        private boolean failOnExecute;

        L3Action(String code, String payload, List<String> journal) {
            this.requirement = new L3Requirement(code);
            this.payload = payload;
            this.journal = journal;
        }

        L3Action failOnExecute() {
            this.failOnExecute = true;
            return this;
        }

        L3Requirement requirement() {
            return this.requirement;
        }

        @Override
        public Class<L3Requirement> requirementType() {
            return L3Requirement.class;
        }

        @Override
        public Class<L3Aggregate> aggregateType() {
            return L3Aggregate.class;
        }

        @Override
        public String execute(L3Aggregate aggregateRoot, L3Requirement requirement) {
            if (this.failOnExecute) {
                throw new IllegalStateException("execute failed: " + requirement.code());
            }
            this.journal.add("execute:" + requirement.code());
            return "result-" + requirement.code();
        }

        @Override
        public void apply(L3Aggregate aggregateRoot, String result) {
            // 无回写
        }

        @Override
        public void compensate(L3Aggregate aggregateRoot, String result) {
            this.journal.add("compensate:" + requirement.code());
        }

        @Override
        public String payload(L3Aggregate aggregateRoot, L3Requirement requirement) {
            return this.payload;
        }
    }

    /** 测试用中继补偿执行器：按 actionName 路由，可配置前 N 次调用失败。 */
    private static final class RecordingHandler implements ICompensationHandler {

        private final String actionName;
        private final List<String> journal;
        private int remainingFailures;

        RecordingHandler(String actionName, List<String> journal) {
            this.actionName = actionName;
            this.journal = journal;
        }

        RecordingHandler failingFor(int times) {
            this.remainingFailures = times;
            return this;
        }

        @Override
        public String actionName() {
            return this.actionName;
        }

        @Override
        public void compensate(String payload) {
            this.journal.add("relay:" + this.actionName + ":" + payload);
            if (this.remainingFailures > 0) {
                this.remainingFailures--;
                throw new IllegalStateException("relay compensation failed: " + this.actionName);
            }
        }
    }

    /** 基于 openSession(true) 的 ICompensationStatementExecutor 测试实现（事务由 NoopTransactionOperations 控制，不依赖 Spring）。 */
    static class TestStatementExecutor implements ICompensationStatementExecutor {

        private final SqlSessionFactory sqlSessionFactory;

        TestStatementExecutor(SqlSessionFactory sqlSessionFactory) {
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
