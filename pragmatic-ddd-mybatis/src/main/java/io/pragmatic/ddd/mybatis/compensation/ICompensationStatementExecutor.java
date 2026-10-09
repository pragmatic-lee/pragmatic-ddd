package io.pragmatic.ddd.mybatis.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;

import java.util.List;

/**
 * 补偿日志持久化语义执行抽象（mybatis 视角）。
 * 模拟 ICompensationLog 的业务语义方法，额外传入 statementKey（MyBatis 的 namespace.statementId）；
 * 实现不感知 key 具体值，只原样转发给 SqlSession。不负责事务边界（由 MybatisCompensationLog 经 TransactionOperations 控制）。
 *
 * @author wizard-lee
 */
public interface ICompensationStatementExecutor {

    void insert(String statementKey, CompensationRecord record);

    void markExecuted(String statementKey, String actionKey);

    int markCompensating(String statementKey, String actionKey, String claimToken);

    void markCompensated(String statementKey, String actionKey);

    void markFailed(String statementKey, String actionKey, String reason);

    void markConfirmed(String statementKey, String actionKey);

    List<CompensationRecord> findExecuted(String statementKey, int limit);

    List<CompensationRecord> findSuspended(String statementKey, int limit);

    List<CompensationRecord> findFailed(String statementKey, int limit);

    List<CompensationRecord> findRetryableFailed(String statementKey, int limit, int maxAttempts);

    int releaseStaleClaims(String statementKey, long leaseSeconds);
}
