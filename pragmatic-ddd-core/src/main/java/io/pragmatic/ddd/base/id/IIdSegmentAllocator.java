package io.pragmatic.ddd.base.id;

/**
 * 号段分配端口：向底层存储（数据库）申请下一段连续 ID。
 * 框架核心只定义契约，具体实现（DB）由 mybatis 模块提供。
 *
 * @author wizard-lee
 */
public interface IIdSegmentAllocator {

    /**
     * 为指定 bizKey 分配下一段号段（实现需保证并发安全，本框架采用行锁）。
     *
     * @param bizKey 业务键
     * @return 新号段（current = 该段第一个可用 ID）
     */
    IdSegment allocateNext(String bizKey);
}
