package io.pragmatic.ddd.mybatis.id;

import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 基于数据库的号段分配器，实现 core 的 IIdSegmentAllocator 端口。
 *
 * <p>自管独立短事务：allocateNext 内开启 autoCommit=false 会话完成 SELECT ... FOR UPDATE 与 UPDATE，
 * 无论调用方是否处于事务都能正确持锁；模块保持 Spring 无关，仅依赖 MyBatis 核心 API。</p>
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
