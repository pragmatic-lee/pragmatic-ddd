package io.pragmatic.ddd.example.order.application.order.compensation;

import io.pragmatic.ddd.application.compensation.CompensableCommandExecutor;
import io.pragmatic.ddd.application.compensation.CompensationActionRegistry;
import io.pragmatic.ddd.application.compensation.CompensationOptions;
import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 订单域补偿链路装配：按 pragmatic.compensation.mode 在内存模式与 durable 模式间切换。
 *
 * <p>与 {@code OrderRuleConfig}、{@code OrderEventSubscriberRegistry} 同处应用服务层——
 * "哪些动作生效、走哪种模式"是应用层装配决策；基础设施层只负责技术实现。
 * 领域层与应用服务只按 {@link CompensableCommandExecutor} 类型注入，不感知具体模式。</p>
 *
 * <ul>
 *   <li>{@code in-memory}（默认）：不装配补偿日志，覆盖同步失败即补偿，零数据库依赖。</li>
 *   <li>{@code durable}：装配 MyBatis WAL，覆盖同步失败与 JVM 崩溃。</li>
 * </ul>
 *
 * @author wizard-lee
 */
@Configuration
public class OrderCompensationConfig {

    /** 两种模式共用：全部聚合的补偿动作注册表。 */
    @Bean
    public CompensationActionRegistry compensationActionRegistry(
            List<ICompensableAction<?, ?, ?>> actions) {
        return new CompensationActionRegistry(actions);
    }

    /**
     * 内存模式执行器（默认）：不装配补偿日志，覆盖同步失败即补偿。
     *
     * @param commandExecutor 被装饰的 outbox 命令执行器
     * @param registry        补偿动作注册表
     * @return 可补偿命令执行器
     */
    @Bean
    @ConditionalOnProperty(name = "pragmatic.compensation.mode",
                           havingValue = "in-memory",
                           matchIfMissing = true)
    public CompensableCommandExecutor inMemoryCompensableCommandExecutor(
            OutboxCommandExecutor commandExecutor,
            CompensationActionRegistry registry) {
        return new CompensableCommandExecutor(
                commandExecutor,
                null,
                CompensationOptions.inMemory(),
                registry);
    }

    /**
     * durable 模式执行器：装配 MyBatis WAL，覆盖同步失败与 JVM 崩溃。
     *
     * @param commandExecutor 被装饰的 outbox 命令执行器
     * @param compensationLog 持久化补偿日志（durable 专属 Bean）
     * @param registry        补偿动作注册表
     * @return 可补偿命令执行器
     */
    @Bean
    @ConditionalOnProperty(name = "pragmatic.compensation.mode", havingValue = "durable")
    public CompensableCommandExecutor durableCompensableCommandExecutor(
            OutboxCommandExecutor commandExecutor,
            ICompensationLog compensationLog,
            CompensationActionRegistry registry) {
        return new CompensableCommandExecutor(
                commandExecutor,
                compensationLog,
                CompensationOptions.durableMode(),
                registry);
    }
}
