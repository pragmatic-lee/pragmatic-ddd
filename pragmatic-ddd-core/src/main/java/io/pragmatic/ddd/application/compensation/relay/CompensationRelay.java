package io.pragmatic.ddd.application.compensation.relay;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 补偿兜底中继原语：回收超时认领、扫描待补偿记录并重试补偿，超过最大尝试次数后转死信、交人工处理。
 * 补偿调用本身由 ICompensationHandler 提供（按记录的 handler 字段路由）。
 * 本类不内建调度器：只提供"执行一次轮询"的原语，触发时机由使用方决定
 * （Spring @Scheduled、xxl-job、K8s CronJob、运维手动触发）。
 *
 * @author wizard-lee
 */
public class CompensationRelay {

    private static final Logger log = Logger.getLogger(CompensationRelay.class.getName());

    private static final int BATCH_LIMIT = 100;
    private static final int MAX_RELAY_ATTEMPTS = 5;
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(5);

    private final ICompensationLog compensationLog;
    private final List<ICompensationHandler> handlers;

    public CompensationRelay(ICompensationLog compensationLog, List<ICompensationHandler> handlers) {
        this.compensationLog = compensationLog;
        this.handlers = List.copyOf(handlers);
    }

    /** 执行一次轮询：回收超时认领 → 补偿 EXECUTED → 重试 FAILED；超限转死信并告警。 */
    public void pollOnce() {
        int reclaimed = this.compensationLog.releaseStaleClaims(CLAIM_LEASE);
        if (reclaimed > 0) {
            log.warning("compensation relay: reclaimed " + reclaimed + " stale claims");
        }
        // 本轮已处理过的记录不在同一次轮询内二次重试：重试的退避由轮询间隔承担
        Set<String> handled = new HashSet<>();
        this.compensateBatch(this.compensationLog.findExecuted(BATCH_LIMIT), handled);
        this.compensateBatch(this.compensationLog.findRetryableFailed(BATCH_LIMIT, MAX_RELAY_ATTEMPTS), handled);
    }

    private void compensateBatch(List<CompensationRecord> records, Set<String> handled) {
        for (CompensationRecord record : records) {
            if (!handled.add(record.actionKey())) {
                continue;
            }
            String token = UUID.randomUUID().toString();
            boolean claimed = this.compensationLog.markCompensating(record.actionKey(), token);
            if (!claimed) {
                continue;
            }
            Optional<ICompensationHandler> handler = this.handlers.stream()
                    .filter(candidate -> candidate.actionName().equals(record.handler()))
                    .findFirst();
            if (handler.isEmpty()) {
                this.compensationLog.markFailed(record.actionKey(), "no handler for " + record.handler());
                log.warning("compensation relay: no handler for " + record.handler());
                continue;
            }
            this.compensate(record, handler.get());
        }
    }

    private void compensate(CompensationRecord record, ICompensationHandler handler) {
        try {
            handler.compensate(record.payload());
            this.compensationLog.markCompensated(record.actionKey());
        } catch (RuntimeException e) {
            int attempts = record.attempts() + 1;
            this.compensationLog.markFailed(record.actionKey(), e.getMessage());
            if (attempts >= MAX_RELAY_ATTEMPTS) {
                log.severe("compensation relay: dead letter " + record.actionKey() + " -> " + e.getMessage());
            }
        }
    }
}
