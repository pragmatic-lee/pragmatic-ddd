package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.OutboxStatements;
import org.mybatis.spring.SqlSessionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 基于 Spring SqlSessionTemplate 的 IOutboxStatementExecutor 实现（参与 Spring 托管事务）。
 * 每个方法仅把 statementKey + 参数转发给 SqlSessionTemplate，不感知 key 具体值。
 * Spring 绑定集中在示例层，框架核心保持 Spring 无关。
 *
 * @author wizard-lee
 */
public class SpringOutboxStatementExecutor implements IOutboxStatementExecutor {

    private final SqlSessionTemplate sqlSessionTemplate;

    public SpringOutboxStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public void store(String statementKey, List<OutboxMessage> messages) {
        sqlSessionTemplate.insert(statementKey, Map.of("list", messages));
    }

    @Override
    public OutboxMessage claim(String statementKey, String id) {
        sqlSessionTemplate.update(
                statementKey,
                Map.of("id", id, "claimedAt", Instant.now()));
        return sqlSessionTemplate.selectOne(
                OutboxStatements.SELECT_BY_ID,
                Map.of("id", id));
    }

    @Override
    public void markSent(String statementKey, String id) {
        sqlSessionTemplate.update(
                statementKey,
                Map.of("id", id));
    }

    @Override
    public void release(String statementKey, String id) {
        sqlSessionTemplate.update(
                statementKey,
                Map.of("id", id));
    }

    @Override
    public List<OutboxMessage> claimPending(String statementKey, int batchSize, Duration grace) {
        String token = UUID.randomUUID().toString();
        Instant cutoff = Instant.now().minus(grace);
        int claimed = sqlSessionTemplate.update(
                statementKey,
                Map.of("token", token, "cutoff", cutoff, "batchSize", batchSize));
        if (claimed == 0) {
            return List.of();
        }
        return sqlSessionTemplate.selectList(
                OutboxStatements.SELECT_BY_CLAIM_TOKEN,
                Map.of("token", token));
    }

    @Override
    public int incrementAttempts(String statementKey, String id) {
        sqlSessionTemplate.update(
                statementKey,
                Map.of("id", id));
        Integer attempts = sqlSessionTemplate.selectOne(
                OutboxStatements.SELECT_ATTEMPTS,
                Map.of("id", id));
        return attempts == null ? 0 : attempts;
    }

    @Override
    public void markFailed(String statementKey, String id) {
        sqlSessionTemplate.update(
                statementKey,
                Map.of("id", id));
    }
}
