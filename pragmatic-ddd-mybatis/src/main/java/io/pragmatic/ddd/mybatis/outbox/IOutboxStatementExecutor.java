package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;

import java.time.Duration;
import java.util.List;

/**
 * Outbox 持久化语义执行抽象（mybatis 视角）。
 * 模拟 IOutboxStore 的业务语义方法，额外传入 statementKey（MyBatis 的 namespace.statementId）；
 * 实现不感知 key 具体值，只原样转发给 SqlSession。不负责事务边界（由 MybatisOutboxStore 经 TransactionOperations 控制）。
 *
 * @author wizard-lee
 */
public interface IOutboxStatementExecutor {

    void store(String statementKey, List<OutboxMessage> messages);

    OutboxMessage claim(String statementKey, String id);

    void markSent(String statementKey, String id);

    void release(String statementKey, String id);

    List<OutboxMessage> claimPending(String statementKey, int batchSize, Duration grace);

    int incrementAttempts(String statementKey, String id);

    void markFailed(String statementKey, String id);
}
