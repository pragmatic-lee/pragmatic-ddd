package io.pragmatic.ddd.springboot.bridge;

import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.mybatis.id.IIdSegmentStatementExecutor;
import io.pragmatic.ddd.mybatis.id.IdSegmentEntity;
import org.mybatis.spring.SqlSessionTemplate;

import java.util.Map;

/**
 * 基于 Spring {@link SqlSessionTemplate} 的 {@link IIdSegmentStatementExecutor} 实现（参与 Spring 托管事务）。
 * 每个方法仅把 statementKey + 参数转发给 SqlSessionTemplate，不感知 key 的具体值；
 * 事务由 {@code DbSegmentAllocator} 经 {@code TransactionOperations}（REQUIRES_NEW）控制，本类只做纯执行。
 *
 * @author wizard-lee
 */
public class SpringIdSegmentStatementExecutor implements IIdSegmentStatementExecutor {

    private final SqlSessionTemplate sqlSessionTemplate;

    /**
     * 以 Spring 托管的会话模板构建执行器。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     */
    public SpringIdSegmentStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public IdSegment allocateNext(String selectKey, String updateKey, String bizKey) {
        IdSegmentEntity row = this.sqlSessionTemplate.selectOne(selectKey, Map.of("bizKey", bizKey));
        long newMax = row.getCurrentMaxId() + row.getStep();
        this.sqlSessionTemplate.update(updateKey, Map.of("bizKey", bizKey, "newMax", newMax));
        return new IdSegment(row.getCurrentMaxId() + 1, newMax, row.getStep());
    }
}
