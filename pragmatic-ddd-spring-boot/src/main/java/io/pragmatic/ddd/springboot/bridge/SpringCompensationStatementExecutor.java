package io.pragmatic.ddd.springboot.bridge;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.mybatis.compensation.ICompensationStatementExecutor;
import org.mybatis.spring.SqlSessionTemplate;

import java.util.List;
import java.util.Map;

/**
 * 基于 Spring {@link SqlSessionTemplate} 的 {@link ICompensationStatementExecutor} 实现（参与 Spring 托管事务）。
 * 每个方法仅把 statementKey + 参数转发给会话模板，不感知 key 的具体值；
 * 独立短事务（REQUIRES_NEW）由 {@code MybatisCompensationLog} 经 {@code TransactionOperations} 负责，本类只做纯执行。
 *
 * <p>参数键名与 {@code CompensationMapper.xml} 严格对齐，任一侧改名都会导致运行期取参失败。</p>
 *
 * @author wizard-lee
 */
public class SpringCompensationStatementExecutor implements ICompensationStatementExecutor {

    private final SqlSessionTemplate sqlSessionTemplate;

    /**
     * 以 Spring 托管的会话模板构建补偿语句执行器。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     */
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
