package io.pragmatic.ddd.springboot.bridge;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.OutboxStatements;
import org.mybatis.spring.SqlSessionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 Spring {@link SqlSessionTemplate} 的 {@link IOutboxStatementExecutor} 实现（参与 Spring 托管事务）。
 * 每个方法仅把 statementKey + 参数转发给 SqlSessionTemplate，不感知 key 的具体值；
 * 事务边界由 {@code MybatisOutboxStore} 经 {@code TransactionOperations} 控制，本类只做纯执行。
 *
 * @author wizard-lee
 */
public class SpringOutboxStatementExecutor implements IOutboxStatementExecutor {

    private final SqlSessionTemplate sqlSessionTemplate;

    /**
     * 以 Spring 托管的会话模板构建执行器。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     */
    public SpringOutboxStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public void store(String statementKey, List<OutboxMessage> messages) {
        this.sqlSessionTemplate.insert(statementKey, Map.of("list", messages));
    }

    @Override
    public OutboxMessage claim(String statementKey, String id) {
        this.sqlSessionTemplate.update(statementKey, Map.of("id", id, "claimedAt", Instant.now()));
        return this.sqlSessionTemplate.selectOne(OutboxStatements.SELECT_BY_ID, Map.of("id", id));
    }

    @Override
    public void markSent(String statementKey, String id) {
        this.sqlSessionTemplate.update(statementKey, Map.of("id", id));
    }

    @Override
    public void release(String statementKey, String id) {
        this.sqlSessionTemplate.update(statementKey, Map.of("id", id));
    }

    @Override
    public List<OutboxMessage> claimPending(String statementKey, int batchSize, Duration grace) {
        String token = UUID.randomUUID().toString();
        Instant cutoff = Instant.now().minus(grace);
        int claimed = this.sqlSessionTemplate.update(
                statementKey,
                Map.of("token", token, "cutoff", cutoff, "batchSize", batchSize));
        if (claimed == 0) {
            return List.of();
        }
        return this.sqlSessionTemplate.selectList(OutboxStatements.SELECT_BY_CLAIM_TOKEN, Map.of("token", token));
    }

    @Override
    public int incrementAttempts(String statementKey, String id) {
        this.sqlSessionTemplate.update(statementKey, Map.of("id", id));
        Integer attempts = this.sqlSessionTemplate.selectOne(OutboxStatements.SELECT_ATTEMPTS, Map.of("id", id));
        return Optional.ofNullable(attempts).orElse(0);
    }

    @Override
    public void markFailed(String statementKey, String id) {
        this.sqlSessionTemplate.update(statementKey, Map.of("id", id));
    }
}
