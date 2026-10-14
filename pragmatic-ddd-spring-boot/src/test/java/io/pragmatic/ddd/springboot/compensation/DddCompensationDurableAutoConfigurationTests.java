package io.pragmatic.ddd.springboot.compensation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.pragmatic.ddd.application.compensation.CompensableCommandExecutor;
import io.pragmatic.ddd.application.compensation.relay.CompensationRelay;
import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.mybatis.compensation.ICompensationStatementExecutor;
import io.pragmatic.ddd.mybatis.compensation.MybatisCompensationLog;
import io.pragmatic.ddd.springboot.compensation.DddCompensationAutoConfigurationTests.TestReserveAction;
import io.pragmatic.ddd.springboot.lifecycle.DddLifecycleAutoConfiguration;
import io.pragmatic.ddd.springboot.lifecycle.DddStartupLifecycle;
import io.pragmatic.ddd.springboot.outbox.DddOutboxAutoConfiguration;
import io.pragmatic.ddd.springboot.transaction.DddTransactionAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * durable 补偿子装配测试：验证 WAL 日志、中继与就绪后轮询仅在
 * {@code pragmatic-ddd.compensation.mode=durable} 且 MyBatis 底座齐备时产出，
 * 以及两类缺失场景下的显式告警回落。
 *
 * <p>会话模板与事务管理器为 Mock，测试不触库：装配只依赖 Bean 构造，不执行任何 SQL。</p>
 *
 * @author wizard-lee
 */
class DddCompensationDurableAutoConfigurationTests {

    private static final String MODE_DURABLE = "pragmatic-ddd.compensation.mode=durable";

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(SqlSessionTemplate.class, () -> mock(SqlSessionTemplate.class))
            .withBean(IEventSerializer.class, () -> mock(IEventSerializer.class))
            .withBean(IEventManager.class, () -> mock(IEventManager.class))
            .withBean(TestReserveAction.class, TestReserveAction::new)
            .withConfiguration(AutoConfigurations.of(
                    DddTransactionAutoConfiguration.class,
                    DddOutboxAutoConfiguration.class,
                    DddCompensationAutoConfiguration.class,
                    DddLifecycleAutoConfiguration.class));

    /**
     * 内存模式（缺省）下不产出任何数据库相关 Bean，Outbox 与补偿主链照常装配。
     */
    @Test
    void shouldNotAssembleDurableBeansWhenModeIsInMemory() {
        this.baseRunner.run(context -> {
            assertThat(context).hasSingleBean(CompensableCommandExecutor.class);
            assertThat(context).doesNotHaveBean(ICompensationStatementExecutor.class);
            assertThat(context).doesNotHaveBean(ICompensationLog.class);
            assertThat(context).doesNotHaveBean(CompensationRelay.class);
            assertThat(context).doesNotHaveBean(CompensationRelayLifecycle.class);
        });
    }

    /**
     * durable 全链装配：语句执行器、补偿日志、中继与生命周期组件齐备。
     */
    @Test
    void shouldAssembleFullDurableChainWhenHandlerPresent() {
        this.durableRunner().run(context -> {
            assertThat(context).hasSingleBean(ICompensationStatementExecutor.class);
            assertThat(context).hasSingleBean(ICompensationLog.class);
            assertThat(context).hasSingleBean(CompensationRelay.class);
            assertThat(context).hasSingleBean(CompensationRelayLifecycle.class);
            assertThat(context.getBean(ICompensationLog.class)).isExactlyInstanceOf(MybatisCompensationLog.class);
        });
    }

    /**
     * classpath 缺 MyBatis 实现时子块整块不装配，durable 显式回落内存模式并告警一次。
     */
    @Test
    void shouldFallBackToInMemoryWithWarningWhenMybatisIsAbsent() {
        try (WarnCapture capture = new WarnCapture()) {
            this.durableRunner()
                    .withClassLoader(new FilteredClassLoader(MybatisCompensationLog.class))
                    .run(context -> {
                        assertThat(context).hasSingleBean(CompensableCommandExecutor.class);
                        assertThat(context).doesNotHaveBean(ICompensationLog.class);
                        assertThat(capture.warnings()).anyMatch(message -> message.contains("回落 in-memory"));
                    });
        }
    }

    /**
     * 缺业务补偿执行器时保留 WAL 但不挂中继，并告警提示崩溃记录无人回抢。
     */
    @Test
    void shouldSkipRelayWithWarningWhenNoCompensationHandler() {
        try (WarnCapture capture = new WarnCapture()) {
            this.baseRunner
                    .withPropertyValues(MODE_DURABLE)
                    .run(context -> {
                        assertThat(context).hasSingleBean(ICompensationLog.class);
                        assertThat(context).doesNotHaveBean(CompensationRelay.class);
                        assertThat(context).doesNotHaveBean(CompensationRelayLifecycle.class);
                        assertThat(capture.warnings()).anyMatch(message -> message.contains("不会被回抢"));
                    });
        }
    }

    /**
     * 使用方自备补偿日志与中继时默认实现退避，自定义实例生效。
     */
    @Test
    void shouldBackOffWhenUserProvidesCompensationLogAndRelay() {
        ICompensationLog customLog = mock(ICompensationLog.class);
        CompensationRelay customRelay = mock(CompensationRelay.class);
        this.durableRunner()
                .withBean(ICompensationLog.class, () -> customLog)
                .withBean(CompensationRelay.class, () -> customRelay)
                .run(context -> {
                    assertThat(context.getBean(ICompensationLog.class)).isSameAs(customLog);
                    assertThat(context.getBean(CompensationRelay.class)).isSameAs(customRelay);
                    assertThat(ReflectionTestUtils.getField(context.getBean(CompensationRelayLifecycle.class), "relay"))
                            .isSameAs(customRelay);
                });
    }

    /**
     * 相位阶梯与回收归属：中继生命周期晚于统一启动组件挂载；停止只置标记，回收交由 destroyMethod。
     */
    @Test
    void shouldStopRelayBeforeStartupLifecycleAndLetDestroyMethodReclaimScheduler() {
        ScheduledExecutorService[] schedulerHolder = new ScheduledExecutorService[1];
        CompensationRelayLifecycle[] lifecycleHolder = new CompensationRelayLifecycle[1];
        this.durableRunner().run(context -> {
            int startupPhase = context.getBean(DddStartupLifecycle.class).getPhase();
            CompensationRelayLifecycle lifecycle = context.getBean(CompensationRelayLifecycle.class);
            assertThat(lifecycle.getPhase()).isGreaterThan(startupPhase);
            assertThat(lifecycle.isRunning()).isTrue();
            schedulerHolder[0] = context.getBean("dddCompensationRelayScheduler", ScheduledExecutorService.class);
            lifecycleHolder[0] = lifecycle;
            lifecycle.stop();
            assertThat(lifecycle.isRunning()).isFalse();
            assertThat(schedulerHolder[0].isShutdown()).isFalse();
        });
        assertThat(schedulerHolder[0].isShutdown()).isTrue();
        assertThat(lifecycleHolder[0].isRunning()).isFalse();
    }

    private ApplicationContextRunner durableRunner() {
        return this.baseRunner
                .withPropertyValues(MODE_DURABLE)
                .withBean(ICompensationHandler.class, () -> mock(ICompensationHandler.class));
    }

    /**
     * 捕获补偿自动配置类的告警日志，关闭时摘除，避免用例间串扰。
     */
    private static final class WarnCapture implements AutoCloseable {

        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender;

        private WarnCapture() {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            this.logger = loggerContext.getLogger(DddCompensationAutoConfiguration.class);
            this.appender = new ListAppender<>();
            this.appender.setContext(loggerContext);
            this.appender.start();
            this.logger.addAppender(this.appender);
        }

        private List<String> warnings() {
            return this.appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        }

        @Override
        public void close() {
            this.logger.detachAppender(this.appender);
            this.appender.stop();
        }
    }
}
