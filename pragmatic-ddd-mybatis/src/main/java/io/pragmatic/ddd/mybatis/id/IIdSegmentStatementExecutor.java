package io.pragmatic.ddd.mybatis.id;

import io.pragmatic.ddd.base.id.IdSegment;

/**
 * ID 号段持久化语义执行抽象（mybatis 视角）。
 * 模拟 IIdSegmentAllocator.allocateNext 语义，传入 selectKey / updateKey（MyBatis 的 statementId）；
 * 实现不感知 key 具体值，只原样转发给 SqlSession。不负责事务边界（由 DbSegmentAllocator 经 TransactionOperations 控制）。
 *
 * @author wizard-lee
 */
public interface IIdSegmentStatementExecutor {

    IdSegment allocateNext(String selectKey, String updateKey, String bizKey);
}
