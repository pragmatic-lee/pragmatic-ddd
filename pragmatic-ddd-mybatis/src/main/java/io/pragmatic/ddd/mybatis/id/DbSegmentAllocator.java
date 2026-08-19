package io.pragmatic.ddd.mybatis.id;

import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 基于数据库号段的 IIdSegmentAllocator 实现，自管独立短事务持锁分配号段。
 *
 * @author wizard-lee
 */
public class DbSegmentAllocator implements IIdSegmentAllocator {

    private final SqlSessionFactory sqlSessionFactory;

    public DbSegmentAllocator(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public IdSegment allocateNext(String bizKey) {
        try (SqlSession session = sqlSessionFactory.openSession(false)) {
            IdSegmentMapper mapper = session.getMapper(IdSegmentMapper.class);
            IdSegmentEntity row = mapper.selectForUpdate(bizKey);
            long newMax = row.getCurrentMaxId() + row.getStep();
            mapper.incrementMax(bizKey, newMax);
            session.commit();
            return new IdSegment(row.getCurrentMaxId() + 1, newMax, row.getStep());
        }
    }
}
