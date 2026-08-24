package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.application.outbox.OutboxStatus;
import io.pragmatic.ddd.mybatis.MysqlTestSupport;
import io.pragmatic.ddd.mybatis.NoopTransactionOperations;
import io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.OutboxStatements;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisOutboxStoreMysqlTest {

    private static SqlSessionFactory ssf;
    private SqlSession session;
    private MybatisOutboxStore store;

    @BeforeAll
    static void init() throws Exception {
        Assumptions.assumeTrue(MysqlTestSupport.isAvailable(), "MySQL 不可用，跳过集成测试");
        ssf = MysqlTestSupport.sqlSessionFactory();
    }

    @BeforeEach
    void open() throws SQLException {
        session = ssf.openSession(true); // autoCommit，测试简单起见
        // 每次用例前清空 outbox_message，保证跨运行/跨用例隔离（避免主键冲突）。
        try (Statement st = session.getConnection().createStatement()) {
            st.execute("DELETE FROM outbox_message");
        }
        // 传统纯 XML 直调方式：基于 openSession(true) 的 IOutboxStatementExecutor 测试实现构造 store
        store = new MybatisOutboxStore(new TestOutboxStatementExecutor(ssf), new NoopTransactionOperations());
    }

    @AfterEach
    void close() {
        if (session != null) {
            session.close();
        }
    }

    private OutboxMessage selectById(String id) {
        try (SqlSession s = ssf.openSession(true)) {
            return s.selectOne(OutboxStatements.SELECT_BY_ID, id);
        }
    }

    private OutboxMessage newMessage(String id) {
        OutboxMessage m = new OutboxMessage();
        m.setId(id);
        m.setAggregateId("agg-1");
        m.setAggregateType("Order");
        m.setEventType("OrderCreated");
        m.setPayload("{}");
        m.setStatus(OutboxStatus.PENDING);
        m.setAttempts(0);
        m.setQueue(0);
        m.setCreatedAt(Instant.now().minusSeconds(10)); // 默认在 grace 窗口内
        return m;
    }

    @Test
    void store_then_claimPending_then_markSent() throws InterruptedException {
        store.store(List.of(newMessage("id-1")));
        Thread.sleep(2000);

        List<OutboxMessage> claimed = store.claimPending(10, Duration.ofSeconds(1));
        assertThat(claimed).extracting(OutboxMessage::getId)
                .containsExactly("id-1");
        assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);

        store.markSent("id-1");
        OutboxMessage after = selectById("id-1");
        assertThat(after.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(after.getSentAt()).isNotNull();
    }

    @Test
    void markSent_is_idempotent_and_never_overwrites_terminal() {
        store.store(List.of(newMessage("id-2")));
        store.claimPending(10, Duration.ofMinutes(1));

        // 第一次 markSent 成功
        store.markSent("id-2");
        // 重复 markSent 不应报错，也不应改变已终态
        store.markSent("id-2");

        OutboxMessage after = selectById("id-2");
        assertThat(after.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void release_returns_processing_back_to_pending() {
        store.store(List.of(newMessage("id-3")));
        // 消息默认 createdAt=now-10s，grace 取 1s（< 10s）才能被认领为 PROCESSING。
        store.claimPending(10, Duration.ofSeconds(1));
        assertThat(selectById("id-3").getStatus()).isEqualTo(OutboxStatus.PROCESSING);

        store.release("id-3");
        assertThat(selectById("id-3").getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void incrementAttempts_and_markFailed() {
        store.store(List.of(newMessage("id-4")));

        int attempts = store.incrementAttempts("id-4");
        assertThat(attempts).isEqualTo(1);
        assertThat(selectById("id-4").getAttempts()).isEqualTo(1);

        store.markFailed("id-4");
        assertThat(selectById("id-4").getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void claimPending_respects_grace_window_and_batch_size() {
        OutboxMessage old1 = newMessage("old-1");
        old1.setCreatedAt(Instant.now().minusSeconds(180)); // 3 min 前，超过 grace
        OutboxMessage old2 = newMessage("old-2");
        old2.setCreatedAt(Instant.now().minusSeconds(120)); // 2 min 前，超过 grace
        OutboxMessage fresh = newMessage("fresh-1");
        fresh.setCreatedAt(Instant.now());                  // 刚产生，仍在 grace 内

        store.store(List.of(old1, old2, fresh));

        // grace=1min、batchSize=1：仅认领最老的一条
        List<OutboxMessage> claimed = store.claimPending(1, Duration.ofMinutes(1));
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getId()).isEqualTo("old-1");

        // fresh-1 不应被认领
        assertThat(selectById("fresh-1").getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    /** 基于 openSession(true) 的 IOutboxStatementExecutor 测试实现（不依赖 Spring）。 */
    static class TestOutboxStatementExecutor implements IOutboxStatementExecutor {

        private final SqlSessionFactory sqlSessionFactory;

        TestOutboxStatementExecutor(SqlSessionFactory sqlSessionFactory) {
            this.sqlSessionFactory = sqlSessionFactory;
        }

        private SqlSession session() {
            return sqlSessionFactory.openSession(true);
        }

        @Override
        public void store(String statementKey, List<OutboxMessage> messages) {
            try (SqlSession s = session()) {
                s.insert(statementKey, Map.of("list", messages));
            }
        }

        @Override
        public OutboxMessage claim(String statementKey, String id) {
            try (SqlSession s = session()) {
                s.update(statementKey, Map.of("id", id, "claimedAt", Instant.now()));
                return s.selectOne(OutboxStatements.SELECT_BY_ID, Map.of("id", id));
            }
        }

        @Override
        public void markSent(String statementKey, String id) {
            try (SqlSession s = session()) {
                s.update(statementKey, Map.of("id", id));
            }
        }

        @Override
        public void release(String statementKey, String id) {
            try (SqlSession s = session()) {
                s.update(statementKey, Map.of("id", id));
            }
        }

        @Override
        public List<OutboxMessage> claimPending(String statementKey, int batchSize, Duration grace) {
            try (SqlSession s = session()) {
                String token = UUID.randomUUID().toString();
                Instant cutoff = Instant.now().minus(grace);
                int claimed = s.update(statementKey, Map.of("token", token, "cutoff", cutoff, "batchSize", batchSize));
                if (claimed == 0) {
                    return List.of();
                }
                return s.selectList(OutboxStatements.SELECT_BY_CLAIM_TOKEN, Map.of("token", token));
            }
        }

        @Override
        public int incrementAttempts(String statementKey, String id) {
            try (SqlSession s = session()) {
                s.update(statementKey, Map.of("id", id));
                Integer attempts = s.selectOne(OutboxStatements.SELECT_ATTEMPTS, Map.of("id", id));
                return attempts == null ? 0 : attempts;
            }
        }

        @Override
        public void markFailed(String statementKey, String id) {
            try (SqlSession s = session()) {
                s.update(statementKey, Map.of("id", id));
            }
        }
    }
}
