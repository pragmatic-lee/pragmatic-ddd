package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.mybatis.compensation.ICompensationStatementExecutor;
import org.mybatis.spring.SqlSessionTemplate;

import java.util.List;
import java.util.Map;

/**
 * 基于 Spring SqlSessionTemplate 的 ICompensationStatementExecutor 实现（参与 Spring 托管事务）。
 * 每个方法仅把 statementKey + 参数转发给 SqlSessionTemplate，不感知 key 具体值。
 * Spring 绑定集中在示例层，框架核心保持 Spring 无关。
 *
 * @author wizard-lee
 */
public class SpringCompensationStatementExecutor implements ICompensationStatementExecutor {

    private final SqlSessionTemplate sqlSessionTemplate;

    public SpringCompensationStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public void insert(String statementKey, CompensationRecord record) {
        this.sqlSessionTemplate.insert(statementKey, Map.of("record", record));
    }

    @Override
    public void markExecuted(String statementKey, String actionKey) {
        this.sqlSessionTemplate.update(statementKey, Map.of("actionKey", actionKey));
    }

    @Override
    public int markCompensating(String statementKey, String actionKey, String claimToken) {
        return this.sqlSessionTemplate.update(
                statementKey,
                Map.of("actionKey", actionKey, "claimToken", claimToken));
    }

    @Override
    public void markCompensated(String statementKey, String actionKey) {
        this.sqlSessionTemplate.update(statementKey, Map.of("actionKey", actionKey));
    }

    @Override
    public void markFailed(String statementKey, String actionKey, String reason) {
        this.sqlSessionTemplate.update(statementKey, Map.of("actionKey", actionKey, "reason", reason));
    }

    @Override
    public void markConfirmed(String statementKey, String actionKey) {
        this.sqlSessionTemplate.update(statementKey, Map.of("actionKey", actionKey));
    }

    @Override
    public List<CompensationRecord> findExecuted(String statementKey, int limit) {
        return this.sqlSessionTemplate.selectList(statementKey, Map.of("limit", limit));
    }

    @Override
    public List<CompensationRecord> findSuspended(String statementKey, int limit) {
        return this.sqlSessionTemplate.selectList(statementKey, Map.of("limit", limit));
    }

    @Override
    public List<CompensationRecord> findFailed(String statementKey, int limit) {
        return this.sqlSessionTemplate.selectList(statementKey, Map.of("limit", limit));
    }

    @Override
    public List<CompensationRecord> findRetryableFailed(String statementKey, int limit, int maxAttempts) {
        return this.sqlSessionTemplate.selectList(
                statementKey,
                Map.of("limit", limit, "maxAttempts", maxAttempts));
    }

    @Override
    public int releaseStaleClaims(String statementKey, long leaseSeconds) {
        return this.sqlSessionTemplate.update(statementKey, Map.of("leaseSeconds", leaseSeconds));
    }
}
