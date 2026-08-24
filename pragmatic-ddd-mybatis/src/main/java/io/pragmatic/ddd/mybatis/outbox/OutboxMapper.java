package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 表 Mapper 契约接口（可选，与具体数据库无关的契约定义）。
 *
 * <p>仅声明方法签名，不含任何 SQL；具体 SQL 由同包同名 {@code OutboxMapper.xml} 提供（当前为 MySQL）。
 * 框架默认走传统纯 XML 直调方式（MybatisOutboxStore 按 namespace.statementId 直接调用），使用方无需引入本接口；
 * 若偏好接口式仍可 addMapper + getMapper，与纯 XML 扫描不冲突。模块保持 Spring 无关。</p>
 *
 * @author wizard-lee
 */
public interface OutboxMapper {

    /** 同事务批量落库（PENDING）。由调用方事务包裹，本方法自身不开启事务。 */
    void insertBatch(@Param("list") List<OutboxMessage> messages);

    OutboxMessage selectById(@Param("id") String id);

    /** 读取当前重试次数（供 incrementAttempts 返回新值）。 */
    int selectAttempts(@Param("id") String id);

    /** PENDING → PROCESSING（单条认领）。返回受影响行数，0 表示已被其他线程认领。 */
    int claim(@Param("id") String id, @Param("claimedAt") Instant claimedAt);

    /** PENDING/PROCESSING → SENT，带状态守卫（幂等，不覆盖 FAILED/SENT）。 */
    int markSent(@Param("id") String id);

    /** PROCESSING → PENDING（释放回待发送）。 */
    int release(@Param("id") String id);

    /** 重试次数 +1（仅对非终态生效）。返回受影响行数。 */
    int incrementAttempts(@Param("id") String id);

    /** → FAILED（死信，仅对非 SENT 生效）。 */
    int markFailed(@Param("id") String id);

    /**
     * 原子认领一批 PENDING 候选：在同一条 UPDATE 中翻转为 PROCESSING 并打上本实例唯一令牌
     * {@code token}，返回受影响行数。多实例/多集群安全——InnoDB 保证每行只被一个事务翻成功，
     * 因此不会出现两个 Relay 认领到同一批行（杜绝重复发布）。
     * 具体 SQL 由实现接口的 XML 提供（MySQL 为
     * {@code ... WHERE status='PENDING' AND created_at < #{cutoff} ORDER BY created_at ASC LIMIT #{batchSize}}）。
     */
    int claimPending(@Param("token") String token,
                     @Param("cutoff") Instant cutoff,
                     @Param("batchSize") int batchSize);

    /** 取回本实例刚刚认领（打上 {@code token}）的行，供 Relay 发布。 */
    List<OutboxMessage> selectByClaimToken(@Param("token") String token);
}
