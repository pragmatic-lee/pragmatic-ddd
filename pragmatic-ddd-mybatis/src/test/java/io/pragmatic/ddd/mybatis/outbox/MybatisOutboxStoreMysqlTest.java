package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.application.outbox.OutboxStatus;
import io.pragmatic.ddd.mybatis.MysqlTestSupport;
import io.pragmatic.ddd.mybatis.NoopTransactionOperations;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisOutboxStoreMysqlTest {

    private static SqlSessionFactory ssf;
    private SqlSession session;
    private MybatisOutboxStore store;
    private MysqlOutboxMapper mapper;

    @BeforeAll
    static void init() throws Exception {
        ssf = MysqlTestSupport.sqlSessionFactory();
    }

    @BeforeEach
    void open() {
        session = ssf.openSession(true); // autoCommit，测试简单起见
        mapper = session.getMapper(MysqlOutboxMapper.class);
        store = new MybatisOutboxStore(mapper, new NoopTransactionOperations());
    }

    @AfterEach
    void close() {
        if (session != null) {
            session.close();
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
        OutboxMessage after = mapper.selectById("id-1");
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

        OutboxMessage after = mapper.selectById("id-2");
        assertThat(after.getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void release_returns_processing_back_to_pending() {
        store.store(List.of(newMessage("id-3")));
        store.claimPending(10, Duration.ofMinutes(1));
        assertThat(mapper.selectById("id-3").getStatus()).isEqualTo(OutboxStatus.PROCESSING);

        store.release("id-3");
        assertThat(mapper.selectById("id-3").getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void incrementAttempts_and_markFailed() {
        store.store(List.of(newMessage("id-4")));

        int attempts = store.incrementAttempts("id-4");
        assertThat(attempts).isEqualTo(1);
        assertThat(mapper.selectById("id-4").getAttempts()).isEqualTo(1);

        store.markFailed("id-4");
        assertThat(mapper.selectById("id-4").getStatus()).isEqualTo(OutboxStatus.FAILED);
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
        assertThat(mapper.selectById("fresh-1").getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
