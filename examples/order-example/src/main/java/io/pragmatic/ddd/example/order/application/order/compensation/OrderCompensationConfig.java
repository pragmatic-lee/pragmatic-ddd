package io.pragmatic.ddd.example.order.application.order.compensation;

import io.pragmatic.ddd.application.compensation.CompensableCommandExecutor;
import io.pragmatic.ddd.application.compensation.CompensationActionRegistry;
import io.pragmatic.ddd.application.compensation.CompensationOptions;
import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 订单域补偿链路装配（内存模式）：收集全部补偿动作构造注册表，并注入可补偿命令执行器。
 *
 * <p>与 {@code OrderRuleConfig}、{@code OrderEventSubscriberRegistry} 同处应用服务层——
 * "哪些动作生效"是应用层装配决策；基础设施层只负责技术实现（数据源、消息中间件、仓储适配）。
 * 各聚合只需把自己的动作声明为组件（如 {@code ReserveInventoryAction}），此处统一收集，
 * 新增动作无需改动本类。</p>
 *
 * @author wizard-lee
 */
@Configuration
public class OrderCompensationConfig {

    /**
     * 可补偿命令执行器（内存模式）：包装 outbox 执行器，为声明外部需求的命令叠加逆序补偿。
     *
     * <p>内存模式下不装配 ICompensationLog（传 null），覆盖"同步失败即补偿"；
     * JVM 崩溃时补偿依据丢失，如需崩溃安全请切换到 durable 模式。
     * 返回类型声明为具体类型，避免与 OutboxCommandExecutor 按 ICommandExecutor 注入时产生歧义。</p>
     *
     * @param commandExecutor 被装饰的 outbox 命令执行器
     * @param actions         全部补偿动作（各聚合以组件形式提供，此处统一收集）
     * @return 可补偿命令执行器
     */
    @Bean
    public CompensableCommandExecutor compensableCommandExecutor(OutboxCommandExecutor commandExecutor,
                                                                 List<ICompensableAction<?, ?, ?>> actions) {
        return new CompensableCommandExecutor(
                commandExecutor,
                null,
                CompensationOptions.inMemory(),
                new CompensationActionRegistry(actions));
    }
}
