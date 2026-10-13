package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.application.compensation.relay.CompensationRelay;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.mybatis.compensation.ICompensationStatementExecutor;
import io.pragmatic.ddd.mybatis.compensation.MybatisCompensationLog;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 补偿 L3 专属装配（仅 durable 模式生效）：补偿日志、中继与调度。
 * 内存模式下整类不参与装配，不引入任何数据库依赖。
 *
 * @author wizard-lee
 */
@Configuration
@ConditionalOnProperty(name = "pragmatic.compensation.mode", havingValue = "durable")
public class CompensationDurableConfig {

    /**
     * 补偿日志：MyBatis 实现，全部写操作走 REQUIRES_NEW 独立短事务。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     * @param txOps              事务抽象（需支持 REQUIRES_NEW）
     * @return ICompensationLog 实现
     */
    @Bean
    public ICompensationLog compensationLog(SqlSessionTemplate sqlSessionTemplate,
                                            TransactionOperations txOps) {
        ICompensationStatementExecutor executor =
                new SpringCompensationStatementExecutor(sqlSessionTemplate);
        return new MybatisCompensationLog(executor, txOps);
    }

    /**
     * 补偿中继：按记录的 handler 路由到业务提供的 ICompensationHandler。
     *
     * @param compensationLog 补偿日志
     * @param handlers        全部补偿执行器
     * @return 补偿中继
     */
    @Bean
    public CompensationRelay compensationRelay(ICompensationLog compensationLog,
                                               List<ICompensationHandler> handlers) {
        return new CompensationRelay(compensationLog, handlers);
    }

    /**
     * 应用就绪后启动轮询：中继只提供 pollOnce 原语，调度交使用方。
     *
     * @param relay 补偿中继
     * @return 应用就绪回调
     */
    @Bean
    public ApplicationRunner startCompensationRelayOnReady(CompensationRelay relay) {
        return (ApplicationArguments args) -> {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleWithFixedDelay(relay::pollOnce, 10L, 10L, TimeUnit.SECONDS);
        };
    }
}
