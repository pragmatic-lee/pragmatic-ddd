package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;
import io.pragmatic.ddd.application.outbox.OutboxRelay;
import io.pragmatic.ddd.application.outbox.OutboxRelayConfig;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore;
import io.pragmatic.ddd.mybatis.outbox.OutboxMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Outbox 链路装配（对应设计文档 4.2 / 5.2 节）。
 * 集中暴露 TransactionOperations、IEventSerializer、IOutboxStore、EagerOutboxPublisher、
 * OutboxRelay 五个 Bean，并启动兜底轮询。
 *
 * @author wizard-lee
 */
@Configuration
public class OutboxConfig {

    /**
     * 基于 Spring TransactionTemplate 的事务抽象（对应设计文档 4.1 节）。
     *
     * @param transactionManager MySqlConfig 暴露的事务管理器
     * @return TransactionOperations 实现
     */
    @Bean
    public TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
        return new SpringTransactionOperations(transactionManager);
    }

    /**
     * 事件序列化器（fastjson2 最小实现，对应设计文档 7.7 节）。
     *
     * @return IEventSerializer 实现
     */
    @Bean
    public IEventSerializer eventSerializer() {
        return new Fastjson2EventSerializer();
    }

    /**
     * MyBatis 实现的 Outbox 存储。
     * OutboxMapper 不经 @MapperScan 注入，通过 SqlSessionTemplate.getMapper 获取。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     * @param txOps 事务抽象
     * @return IOutboxStore 实现
     */
    @Bean
    public IOutboxStore outboxStore(SqlSessionTemplate sqlSessionTemplate, TransactionOperations txOps) {
        OutboxMapper outboxMapper = sqlSessionTemplate.getMapper(OutboxMapper.class);
        return new MybatisOutboxStore(outboxMapper, txOps);
    }

    /**
     * 提交后主动推送器：事务提交后异步发送原始事件。
     *
     * @param outboxStore Outbox 存储
     * @param eventManager 事件管理器
     * @return EagerOutboxPublisher 实例
     */
    @Bean
    public EagerOutboxPublisher eagerOutboxPublisher(IOutboxStore outboxStore, IEventManager eventManager) {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        return new EagerOutboxPublisher(outboxStore, eventManager, pool);
    }

    /**
     * 兜底轮询器：周期补偿超时 PENDING 记录，构造后立即启动。
     *
     * @param outboxStore Outbox 存储
     * @param eventManager 事件管理器
     * @param serializer 事件序列化器
     * @return 已启动的 OutboxRelay 实例
     */
    @Bean
    public OutboxRelay outboxRelay(IOutboxStore outboxStore,
                                   IEventManager eventManager,
                                   IEventSerializer serializer) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        OutboxRelay relay = new OutboxRelay(outboxStore, eventManager, serializer, scheduler,
                OutboxRelayConfig.defaultConfig());
        relay.start();
        return relay;
    }
}
