package io.pragmatic.ddd.application.compensation.spi;

import io.pragmatic.ddd.application.compensation.CompensationStatus;

import java.time.Instant;

/**
 * 补偿日志行。
 *
 * @param actionKey 幂等键（主键，须在一次补偿范围内唯一）
 * @param status    状态
 * @param handler   补偿执行器路由键，指向 ICompensationHandler#actionName
 * @param payload   补偿所需业务标识（如预占单号）的序列化内容，用于跨进程恢复
 * @param attempts  已尝试补偿次数
 * @param updatedAt 更新时间
 * @author wizard-lee
 */
public record CompensationRecord(String actionKey,
                                 CompensationStatus status,
                                 String handler,
                                 String payload,
                                 int attempts,
                                 Instant updatedAt) {
}
