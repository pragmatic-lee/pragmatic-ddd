package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.mybatis.id.IdSegmentEntity;
import io.pragmatic.ddd.mybatis.id.IIdSegmentStatementExecutor;
import org.mybatis.spring.SqlSessionTemplate;

import java.util.Map;

/**
 * 基于 Spring SqlSessionTemplate 的 IIdSegmentStatementExecutor 实现（参与 Spring 托管事务）。
 * 每个方法仅把 statementKey + 参数转发给 SqlSessionTemplate，不感知 key 具体值；
 * 事务由 DbSegmentAllocator 经 TransactionOperations（REQUIRES_NEW）控制，本类只做纯执行。
 * Spring 绑定集中在示例层，框架核心保持 Spring 无关。
 *
 * @author wizard-lee
 */
public class SpringIdSegmentStatementExecutor implements IIdSegmentStatementExecutor {

    private final SqlSessionTemplate sqlSessionTemplate;

    public SpringIdSegmentStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    @Override
    public IdSegment allocateNext(String selectKey, String updateKey, String bizKey) {
        IdSegmentEntity row = sqlSessionTemplate.selectOne(
                selectKey,
                Map.of("bizKey", bizKey));
        long newMax = row.getCurrentMaxId() + row.getStep();
        sqlSessionTemplate.update(
                updateKey,
                Map.of("bizKey", bizKey, "newMax", newMax));
        return new IdSegment(row.getCurrentMaxId() + 1, newMax, row.getStep());
    }
}
