package io.pragmatic.ddd.springboot.outbox;

import io.pragmatic.ddd.application.IUnitOfWork;
import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.application.outbox.OutboxRelay;
import io.pragmatic.ddd.application.outbox.OutboxUnitOfWork;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore;
import io.pragmatic.ddd.springboot.bridge.SpringOutboxStatementExecutor;
import io.pragmatic.ddd.springboot.transaction.DddTransactionAutoConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Outbox 链路自动装配：集中产出执行器、存储、主动推送器、兜底轮询器、命令执行器与工作单元工厂，
 * 把聚合写与 outbox 写绑定到同一事务、提交后主动推送、超时由轮询兜底。
 *
 * <p>仅当 classpath 存在 MyBatis 集成、容器中有 {@link SqlSessionTemplate}/{@link TransactionOperations}/
 * {@link IEventSerializer}/{@link IEventManager} 且 {@code pragmatic-ddd.outbox.enabled} 未关闭时生效。
 * 每个 Bean 均可由使用方声明同类型 Bean 覆盖。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration(after = DddTransactionAutoConfiguration.class)
@ConditionalOnClass({MybatisOutboxStore.class, SqlSessionTemplate.class})
@ConditionalOnBean({SqlSessionTemplate.class, TransactionOperations.class, IEventSerializer.class, IEventManager.class})
@ConditionalOnProperty(prefix = "pragmatic-ddd.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DddOutboxProperties.class)
public class DddOutboxAutoConfiguration {

    /**
     * MyBatis Outbox 语句执行器：把语句键 + 参数转发给 Spring 托管的会话模板。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     * @return 语句执行器
     */
    @Bean
    @ConditionalOnMissingBean(IOutboxStatementExecutor.class)
    public IOutboxStatementExecutor outboxStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        return new SpringOutboxStatementExecutor(sqlSessionTemplate);
    }

    /**
     * MyBatis 实现的 Outbox 存储。
     *
     * @param executor  语句执行器
     * @param txOps     事务抽象
     * @return Outbox 存储
     */
    @Bean
    @ConditionalOnMissingBean(IOutboxStore.class)
    public IOutboxStore outboxStore(IOutboxStatementExecutor executor, TransactionOperations txOps) {
        return new MybatisOutboxStore(executor, txOps);
    }

    /**
     * 提交后主动推送专用的有界线程池，随容器关闭回收。
     *
     * @param properties outbox 配置
     * @return 线程池
     */
    @Bean(name = "dddOutboxPublisherExecutor", destroyMethod = "shutdown")
    public ExecutorService dddOutboxPublisherExecutor(DddOutboxProperties properties) {
        return Executors.newFixedThreadPool(properties.getPublisherPoolSize());
    }

    /**
     * 兜底轮询专用的单线程调度器，随容器关闭回收。
     *
     * @return 调度器
     */
    @Bean(name = "dddOutboxRelayScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService dddOutboxRelayScheduler() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * 提交后主动推送器。
     *
     * @param outboxStore  Outbox 存储
     * @param eventManager 事件管理器
     * @param pool         推送线程池
     * @return 主动推送器
     */
    @Bean
    @ConditionalOnMissingBean(EagerOutboxPublisher.class)
    public EagerOutboxPublisher eagerOutboxPublisher(IOutboxStore outboxStore,
                                                     IEventManager eventManager,
                                                     @Qualifier("dddOutboxPublisherExecutor") ExecutorService pool) {
        return new EagerOutboxPublisher(outboxStore, eventManager, pool);
    }

    /**
     * 兜底轮询器（仅构造，启动交由 {@code DddStartupLifecycle} 在应用就绪后触发）。
     *
     * @param outboxStore  Outbox 存储
     * @param eventManager 事件管理器
     * @param serializer   事件序列化器
     * @param scheduler    轮询调度器
     * @param properties   outbox 配置
     * @return 未启动的兜底轮询器
     */
    @Bean
    @ConditionalOnMissingBean(OutboxRelay.class)
    public OutboxRelay outboxRelay(IOutboxStore outboxStore,
                                   IEventManager eventManager,
                                   IEventSerializer serializer,
                                   @Qualifier("dddOutboxRelayScheduler") ScheduledExecutorService scheduler,
                                   DddOutboxProperties properties) {
        return new OutboxRelay(outboxStore, eventManager, serializer, scheduler, properties.toRelayConfig());
    }

    /**
     * Outbox 语义命令执行器成品：应用服务直接注入即可。
     *
     * @param outboxStore    Outbox 存储
     * @param txOps          事务抽象
     * @param serializer     事件序列化器
     * @param eagerPublisher 提交后主动推送器
     * @return 命令执行器
     */
    @Bean
    @ConditionalOnMissingBean(OutboxCommandExecutor.class)
    public OutboxCommandExecutor outboxCommandExecutor(IOutboxStore outboxStore,
                                                       TransactionOperations txOps,
                                                       IEventSerializer serializer,
                                                       EagerOutboxPublisher eagerPublisher) {
        return new OutboxCommandExecutor(outboxStore, txOps, serializer, eagerPublisher);
    }

    /**
     * Outbox 语义工作单元工厂：每次 get() 产出全新 {@link OutboxUnitOfWork}（工作单元有状态、单次消费）。
     *
     * @param outboxStore    Outbox 存储
     * @param txOps          事务抽象
     * @param serializer     事件序列化器
     * @param eagerPublisher 提交后主动推送器
     * @return 工作单元工厂
     */
    @Bean("outboxUnitOfWorkFactory")
    public Supplier<IUnitOfWork> outboxUnitOfWorkFactory(IOutboxStore outboxStore,
                                                         TransactionOperations txOps,
                                                         IEventSerializer serializer,
                                                         EagerOutboxPublisher eagerPublisher) {
        return () -> new OutboxUnitOfWork(outboxStore, txOps, serializer, eagerPublisher);
    }
}
