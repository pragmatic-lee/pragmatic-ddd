package io.pragmatic.ddd.springboot.compensation;

import io.pragmatic.ddd.application.compensation.CompensableCommandExecutor;
import io.pragmatic.ddd.application.compensation.CompensationActionRegistry;
import io.pragmatic.ddd.application.compensation.CompensationOptions;
import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.application.compensation.relay.CompensationRelay;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.mybatis.compensation.ICompensationStatementExecutor;
import io.pragmatic.ddd.mybatis.compensation.MybatisCompensationLog;
import io.pragmatic.ddd.springboot.bridge.SpringCompensationStatementExecutor;
import io.pragmatic.ddd.springboot.outbox.DddOutboxAutoConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**
 * 补偿链路自动装配：把 {@link OutboxCommandExecutor} 装饰为可补偿命令执行器。
 * 装配点在此收敛，领域层与应用服务不感知补偿；补偿动作（{@link ICompensableAction}）本身是业务提供。
 *
 * <p>仅当容器同时存在 {@link OutboxCommandExecutor} 与至少一个 {@link ICompensableAction} 时生效。
 * {@code pragmatic-ddd.compensation.mode=durable} 时由嵌套的 {@link DurableCompensationConfiguration} 自动装配持久化
 * 补偿日志（WAL）、中继与就绪后的轮询，前提是 classpath 存在 MyBatis 集成且容器中有 {@link SqlSessionTemplate}；
 * 缺省为内存模式（不落 WAL，覆盖“同步失败即补偿”），不引入任何数据库依赖。</p>
 *
 * <p>本块以 {@link OutboxCommandExecutor} 为装配前置，故必须显式声明排在
 * {@link DddOutboxAutoConfiguration} 之后：自动配置的求值顺序由 before/after 约束与类名字典序共同决定，
 * 缺少声明时 {@code compensation} 会排在 {@code outbox} 之前，导致条件求值时被装饰的 Bean 尚未注册而整块静默跳过。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration(after = DddOutboxAutoConfiguration.class)
@ConditionalOnBean({OutboxCommandExecutor.class, ICompensableAction.class})
@ConditionalOnProperty(prefix = "pragmatic-ddd.compensation", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DddCompensationProperties.class)
public class DddCompensationAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DddCompensationAutoConfiguration.class);

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
     * @param relayProvider   补偿中继提供者（durable 且业务提供了补偿执行器时存在）
     * @param properties      补偿链路配置
     * @return 可补偿命令执行器
     */
    @Bean
    @ConditionalOnMissingBean(CompensableCommandExecutor.class)
    public CompensableCommandExecutor compensableCommandExecutor(OutboxCommandExecutor commandExecutor,
                                                                CompensationActionRegistry registry,
                                                                ObjectProvider<ICompensationLog> logProvider,
                                                                ObjectProvider<CompensationRelay> relayProvider,
                                                                DddCompensationProperties properties) {
        Optional<ICompensationLog> compensationLog = Optional.ofNullable(logProvider.getIfAvailable());
        CompensationOptions options = this.resolveOptions(properties.getMode(), compensationLog);
        if (options.durable() && relayProvider.getIfAvailable() == null) {
            LOG.warn("durable 补偿已生效但容器无 CompensationRelay（业务未提供 ICompensationHandler），"
                    + "崩溃后落入 WAL 的记录不会被回抢");
        }
        return new CompensableCommandExecutor(
                commandExecutor,
                compensationLog.orElse(null),
                options,
                registry);
    }

    private CompensationOptions resolveOptions(String mode, Optional<ICompensationLog> compensationLog) {
        if (!MODE_DURABLE.equalsIgnoreCase(mode)) {
            return CompensationOptions.inMemory();
        }
        if (compensationLog.isEmpty()) {
            LOG.warn("pragmatic-ddd.compensation.mode=durable 但容器无 ICompensationLog Bean，回落 in-memory；"
                    + "请确认已引入 pragmatic-ddd-mybatis 且容器存在 SqlSessionTemplate");
            return CompensationOptions.inMemory();
        }
        return CompensationOptions.durableMode();
    }

    /**
     * durable 子装配：补偿日志（WAL）、中继与就绪后的轮询。
     * 数据库相关产物全部收敛在本块内，未开启 durable、缺 MyBatis 或缺会话模板时整块不装配。
     *
     * <p>中继与调度组件的条件取自业务侧的 {@link ICompensationHandler}（先于自动配置注册，稳定），
     * 不依赖本类自己产出的 {@link CompensationRelay}，避开同类内跨方法条件依赖的脆弱写法。</p>
     *
     * @author wizard-lee
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "pragmatic-ddd.compensation", name = "mode", havingValue = MODE_DURABLE)
    @ConditionalOnClass(MybatisCompensationLog.class)
    @ConditionalOnBean(SqlSessionTemplate.class)
    static class DurableCompensationConfiguration {

        /**
         * 补偿日志的 MyBatis 语句执行器：语句键 + 参数原样转发给 Spring 托管的会话模板。
         *
         * @param sqlSessionTemplate MyBatis 会话模板
         * @return 补偿语句执行器
         */
        @Bean
        @ConditionalOnMissingBean(ICompensationStatementExecutor.class)
        public ICompensationStatementExecutor compensationStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
            return new SpringCompensationStatementExecutor(sqlSessionTemplate);
        }

        /**
         * 持久化补偿日志：写操作走 REQUIRES_NEW 独立短事务，保证主事务回滚时 WAL 仍在。
         *
         * @param executor 补偿语句执行器
         * @param txOps    事务抽象
         * @return 补偿日志
         */
        @Bean
        @ConditionalOnMissingBean(ICompensationLog.class)
        public ICompensationLog compensationLog(ICompensationStatementExecutor executor,
                                                TransactionOperations txOps) {
            return new MybatisCompensationLog(executor, txOps);
        }

        /**
         * 补偿中继：按记录的 handler 编码路由到业务提供的 {@link ICompensationHandler}。
         *
         * @param compensationLog 持久化补偿日志
         * @param handlers        全部补偿执行器
         * @return 补偿中继
         */
        @Bean
        @ConditionalOnBean(ICompensationHandler.class)
        @ConditionalOnMissingBean(CompensationRelay.class)
        public CompensationRelay compensationRelay(ICompensationLog compensationLog,
                                                   List<ICompensationHandler> handlers) {
            return new CompensationRelay(compensationLog, handlers);
        }

        /**
         * 补偿轮询专用的单线程调度器，随容器关闭回收。
         *
         * @return 调度器
         */
        @Bean(name = "dddCompensationRelayScheduler", destroyMethod = "shutdown")
        @ConditionalOnBean(ICompensationHandler.class)
        public ScheduledExecutorService dddCompensationRelayScheduler() {
            return Executors.newSingleThreadScheduledExecutor();
        }

        /**
         * 中继的就绪后启动组件：容器就绪后按 {@code relay-poll-interval} 挂载轮询。
         *
         * @param relay      补偿中继
         * @param scheduler  轮询调度器
         * @param properties 补偿链路配置
         * @return 生命周期组件
         */
        @Bean
        @ConditionalOnBean(ICompensationHandler.class)
        @ConditionalOnMissingBean(CompensationRelayLifecycle.class)
        public CompensationRelayLifecycle compensationRelayLifecycle(
                CompensationRelay relay,
                @Qualifier("dddCompensationRelayScheduler") ScheduledExecutorService scheduler,
                DddCompensationProperties properties) {
            return new CompensationRelayLifecycle(relay, scheduler, properties.getRelayPollInterval());
        }
    }
}
