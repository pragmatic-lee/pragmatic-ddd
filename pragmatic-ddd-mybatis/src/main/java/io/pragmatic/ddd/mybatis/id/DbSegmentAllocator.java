package io.pragmatic.ddd.mybatis.id;

import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * 基于数据库的号段分配器，实现 core 的 IIdSegmentAllocator 端口。
 *
 * <p>本类<b>自管独立事务</b>：在 allocateNext 内通过 SqlSessionFactory 开启
 * autoCommit=false 的会话，依次执行 selectForUpdate → 计算新上限 → incrementMax，
 * 最后 commit() 关闭会话。这样 SELECT ... FOR UPDATE 与 UPDATE 落在同一独立短事务内，
 * 无论调用方是否处于事务都能正确持锁（独立事务语义等同 REQUIRES_NEW）。
 * 模块保持 Spring 无关：仅依赖 MyBatis 核心 API，不引入 Spring 编译依赖。</p>
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
