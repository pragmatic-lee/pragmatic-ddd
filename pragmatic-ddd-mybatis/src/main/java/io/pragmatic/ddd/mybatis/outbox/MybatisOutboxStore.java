package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.mybatis.outbox.OutboxMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@link IOutboxStore} 的官方 MyBatis 实现（事件箱持久化）。
 *
 * <h2>铁律约束</h2>
 * <ul>
 *   <li><b>store 同事务</b>：{@link #store} 在<b>调用方事务</b>内执行（与聚合同事务落库）。
 *       本方法本身不开启任何事务，直接调用 {@link OutboxMapper#insertBatch}，依赖底层
 *       {@code SqlSession} 与聚合写在同一个 Spring 管理事务中。</li>
 *   <li><b>补偿操作独立短事务</b>：{@code claim / claimPending / markSent / release /
 *       incrementAttempts / markFailed} 各自包裹在注入的 {@link TransactionOperations} 内，
 *       作为<b>独立短事务</b>立即提交；在 Spring 场景该 {@code TransactionOperations} 由
 *       {@code TransactionTemplate(Propagation.REQUIRES_NEW)} 提供——<b>绝不</b>包裹 MQ 发送。</li>
 *   <li><b>markSent 幂等守卫</b>：{@code UPDATE ... SET status=SENT WHERE id=? AND status IN ('PENDING','PROCESSING')}，
 *       避免覆盖已被置为 {@code FAILED} / {@code SENT} 的行。</li>
 * </ul>
 *
 * <h2>与 markSent 铁律文本的差异说明</h2>
 * 提案 §7.1 文字写作 {@code WHERE status=PENDING}；但 {@link IOutboxStore} 接口 JavaDoc 与
 * {@code claimPending} 的 {@code PROCESSING} 翻转语义要求 markSent 也须接纳 {@code PROCESSING}。
 * 本实现以「<b>不覆盖 FAILED/SENT</b>」为唯一硬约束，故守卫写作
 * {@code IN ('PENDING','PROCESSING')}——既满足接口契约，又保留 PROCESSING→SENT 的合法路径。
 *
 * @author Li XiaoJing
 * @since 2.5.0
 */
public class MybatisOutboxStore implements IOutboxStore {

    private final OutboxMapper mapper;
    private final TransactionOperations txOps;

    public MybatisOutboxStore(OutboxMapper mapper, TransactionOperations txOps) {
        this.mapper = mapper;
        this.txOps = txOps;
    }

    @Override
    public void store(List<OutboxMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        mapper.insertBatch(messages);   // 调用方事务内（与聚合同事务）
    }

    @Override
    public OutboxMessage claim(String id) {
        return txOps.execute(() -> {
            int n = mapper.claim(id, Instant.now());
            return n > 0 ? mapper.selectById(id) : null;
        });
    }

    @Override
    public void markSent(String id) {
        txOps.execute(() -> {
            mapper.markSent(id);
            return null;
        });
    }

    @Override
    public void release(String id) {
        txOps.execute(() -> {
            mapper.release(id);
            return null;
        });
    }

    @Override
    public List<OutboxMessage> claimPending(int batchSize, Duration grace) {
        Instant cutoff = Instant.now().minus(grace);
        return txOps.execute(() -> {
            // 原子认领：一条 UPDATE 把一批 PENDING 翻为 PROCESSING 并打上本实例唯一令牌，
            // 多实例/多集群下每行只会被一个事务翻成功，返回受影响行数
            String token = UUID.randomUUID().toString();
            int claimed = mapper.claimPending(token, cutoff, batchSize);
            if (claimed == 0) {
                return List.of();
            }
            // 取回本实例认领的行（按令牌精确匹配，零重叠）
            return mapper.selectByClaimToken(token);
        });
    }

    @Override
    public int incrementAttempts(String id) {
        return txOps.execute(() -> {
            int n = mapper.incrementAttempts(id);
            return n > 0 ? mapper.selectAttempts(id) : 0;
        });
    }

    @Override
    public void markFailed(String id) {
        txOps.execute(() -> {
            mapper.markFailed(id);
            return null;
        });
    }
}
