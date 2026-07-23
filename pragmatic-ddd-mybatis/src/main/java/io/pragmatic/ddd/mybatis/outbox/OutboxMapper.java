package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 表 Mapper <b>通用接口</b>（与具体数据库无关的契约定义）。
 *
 * <p>本接口仅声明方法签名，<b>不含任何 SQL</b>。具体 SQL 由各数据库实现
 * （如 {@link MysqlOutboxMapper}）通过各自的 XML 提供。MyBatis 在运行期为
 * <b>具体实现接口</b>（而非本接口本身）生成 JDK 动态代理（{@code MapperProxy}）并绑定
 * XML 中的 SQL——因此无需手写实现类，「实现」即「接口 + XML」。</p>
 *
 * <p>本模块仅内置 MySQL 实现；其它数据库（PostgreSQL / H2 等）由使用方按相同方式
 * 扩展本接口（例如 {@code interface PgOutboxMapper extends OutboxMapper}）并提供各自的
 * XML 即可。<b>{@link MybatisOutboxStore} 仅依赖本通用接口</b>，不耦合任何具体库，
 * 从而可在运行期注入不同实现完成数据库切换。</p>
 *
 * <p>注意：本接口以及各具体实现接口<b>均不标注</b> {@code @Mapper}，整个模块保持 Spring 无关、
 * 采用与 type handler 一致的<b>手动注册</b>风格——使用方在构建 {@code SqlSessionFactory} 后调用
 * {@code configuration.addMapper(MysqlOutboxMapper.class)}（或外部扩展的 {@code XxxOutboxMapper}）
 * 即可，同级 XML 会被 MyBatis 自动加载并绑定。如此既避免为 {@code mybatis} 模块引入 Spring 编译期依赖，
 * 也杜绝「通用契约被误扫描注册却无 XML」的启动失败。</p>
 *
 * @author Li XiaoJing
 * @since 2.5.0
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
