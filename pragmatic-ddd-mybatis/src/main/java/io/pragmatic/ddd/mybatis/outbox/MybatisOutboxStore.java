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
 * <ul>
 *   <li><b>store 同事务</b>：{@link #store} 在调用方事务内执行，与聚合同事务落库，自身不开启事务。</li>
 *   <li><b>补偿操作独立短事务</b>：claim / claimPending / markSent / release / incrementAttempts / markFailed
 *       各自包裹在注入的 {@link TransactionOperations} 内作为独立短事务立即提交，绝不包裹 MQ 发送。</li>
 *   <li><b>markSent 幂等守卫</b>：仅当状态为 PENDING 或 PROCESSING 时才置为 SENT，避免覆盖 FAILED/SENT 的行。</li>
 * </ul>
 *
 * @author wizard-lee
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
