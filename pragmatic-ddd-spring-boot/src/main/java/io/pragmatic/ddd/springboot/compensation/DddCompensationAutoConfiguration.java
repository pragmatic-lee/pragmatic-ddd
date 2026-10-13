package io.pragmatic.ddd.springboot.compensation;

import io.pragmatic.ddd.application.compensation.CompensableCommandExecutor;
import io.pragmatic.ddd.application.compensation.CompensationActionRegistry;
import io.pragmatic.ddd.application.compensation.CompensationOptions;
import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 补偿链路自动装配：把 {@link OutboxCommandExecutor} 装饰为可补偿命令执行器。
 * 装配点在此收敛，领域层与应用服务不感知补偿；补偿动作（{@link ICompensableAction}）本身是业务提供。
 *
 * <p>仅当容器同时存在 {@link OutboxCommandExecutor} 与至少一个 {@link ICompensableAction} 时生效。
 * {@code pragmatic-ddd.compensation.mode=durable} 时使用持久化补偿日志（需另提供 {@link ICompensationLog} Bean），
 * 缺省为内存模式（不落 WAL，覆盖“同步失败即补偿”）。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration
@ConditionalOnBean({OutboxCommandExecutor.class, ICompensableAction.class})
@ConditionalOnProperty(prefix = "pragmatic-ddd.compensation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DddCompensationAutoConfiguration {

    /** 持久化模式标识。 */
    private static final String MODE_DURABLE = "durable";

    /**
     * 补偿动作注册表：收集容器内全部可补偿动作按需求类型建索引。
     *
     * @param actionsProvider 可补偿动作提供者
     * @return 补偿动作注册表
     */
    @Bean
    @ConditionalOnMissingBean(CompensationActionRegistry.class)
    public CompensationActionRegistry compensationActionRegistry(
            ObjectProvider<ICompensableAction<?, ?, ?>> actionsProvider) {
        List<ICompensableAction<?, ?, ?>> actions = actionsProvider.orderedStream()
                .collect(Collectors.toList());
        return new CompensationActionRegistry(actions);
    }

    /**
     * 可补偿命令执行器：包装 outbox 执行器，为声明外部需求的命令叠加逆序补偿。
     * 返回具体类型，避免与应用内其他 {@code ICommandExecutor} 按接口注入时产生歧义。
     *
     * @param commandExecutor 被装饰的 outbox 命令执行器
     * @param registry        补偿动作注册表
     * @param logProvider     持久化补偿日志提供者（内存模式下可为空）
     * @param mode            补偿模式（in-memory | durable）
     * @return 可补偿命令执行器
     */
    @Bean
    @ConditionalOnMissingBean(CompensableCommandExecutor.class)
    public CompensableCommandExecutor compensableCommandExecutor(OutboxCommandExecutor commandExecutor,
                                                                CompensationActionRegistry registry,
                                                                ObjectProvider<ICompensationLog> logProvider,
                                                                @Value("${pragmatic-ddd.compensation.mode:in-memory}")
                                                                        String mode) {
        CompensationOptions options = this.resolveOptions(mode, logProvider);
        ICompensationLog compensationLog = logProvider.getIfAvailable();
        return new CompensableCommandExecutor(commandExecutor, compensationLog, options, registry);
    }

    private CompensationOptions resolveOptions(String mode, ObjectProvider<ICompensationLog> logProvider) {
        if (MODE_DURABLE.equalsIgnoreCase(mode) && logProvider.getIfAvailable() != null) {
            return CompensationOptions.durableMode();
        }
        return CompensationOptions.inMemory();
    }
}
