package io.pragmatic.ddd.application.compensation.relay;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 补偿兜底中继原语：扫描待补偿记录并重试补偿，超过最大尝试次数后转死信、交人工处理。
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

    private final ICompensationLog compensationLog;
    private final List<ICompensationHandler> handlers;

    public CompensationRelay(ICompensationLog compensationLog, List<ICompensationHandler> handlers) {
        this.compensationLog = compensationLog;
        this.handlers = List.copyOf(handlers);
    }

    /** 执行一次轮询：取待补偿记录 → 原子认领 → 路由补偿 → 标记结果；超限转死信并告警。 */
    public void pollOnce() {
        List<CompensationRecord> pending = compensationLog.findExecuted(BATCH_LIMIT);
        for (CompensationRecord record : pending) {
            String token = UUID.randomUUID().toString();
            boolean claimed = compensationLog.markCompensating(record.actionKey(), token);
            if (!claimed) {
                continue;
            }
            Optional<ICompensationHandler> handler = handlers.stream()
                    .filter(candidate -> candidate.actionName().equals(record.handler()))
                    .findFirst();
            if (handler.isEmpty()) {
                compensationLog.markFailed(record.actionKey(), "no handler for " + record.handler());
                log.warning("compensation relay: no handler for " + record.handler());
                continue;
            }
            compensate(record, handler.get());
        }
    }

    private void compensate(CompensationRecord record, ICompensationHandler handler) {
        try {
            handler.compensate(record.payload());
            compensationLog.markCompensated(record.actionKey());
        } catch (RuntimeException e) {
            int attempts = record.attempts() + 1;
            compensationLog.markFailed(record.actionKey(), e.getMessage());
            if (attempts >= MAX_RELAY_ATTEMPTS) {
                log.severe("compensation relay: dead letter " + record.actionKey() + " -> " + e.getMessage());
            }
        }
    }
}
