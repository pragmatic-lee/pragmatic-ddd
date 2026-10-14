package io.pragmatic.ddd.springboot.compensation;

import io.pragmatic.ddd.application.compensation.CompensableCommandExecutor;
import io.pragmatic.ddd.application.compensation.CompensationActionRegistry;
import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.springboot.outbox.DddOutboxAutoConfiguration;
import io.pragmatic.ddd.springboot.transaction.DddTransactionAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 补偿链路自动装配测试：锁死 {@link DddCompensationAutoConfiguration} 排在
 * {@link DddOutboxAutoConfiguration} 之后这一顺序不变式，并覆盖跳过、关块与覆盖三条路径。
 *
 * <p>自动配置类被刻意乱序传入：{@code AutoConfigurations} 会按生产同款规则
 * （before/after 约束 + 类名字典序）重排，因此缺少 {@code after} 声明时补偿块会被跳过。</p>
 *
 * <p>Outbox 块所需的外部协作者（事务管理器、会话模板、事件管理器与序列化器）一律以 Mock 提供，
 * 测试不依赖数据库与消息中间件。</p>
 *
 * @author wizard-lee
 */
class DddCompensationAutoConfigurationTests {

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(SqlSessionTemplate.class, () -> mock(SqlSessionTemplate.class))
            .withBean(IEventSerializer.class, () -> mock(IEventSerializer.class))
            .withBean(IEventManager.class, () -> mock(IEventManager.class))
            .withConfiguration(AutoConfigurations.of(
                    DddCompensationAutoConfiguration.class,
                    DddOutboxAutoConfiguration.class,
                    DddTransactionAutoConfiguration.class));

    /**
     * 补偿块应在 Outbox 之后装配：乱序传入时两个执行器均需就位，且装饰关系成立。
     */
    @Test
    void shouldAssembleCompensableExecutorAfterOutboxExecutor() {
        this.runnerWithAction().run(context -> {
            assertThat(context).hasSingleBean(OutboxCommandExecutor.class);
            assertThat(context).hasSingleBean(CompensableCommandExecutor.class);
            assertThat(ReflectionTestUtils.getField(context.getBean(CompensableCommandExecutor.class), "delegate"))
                    .isSameAs(context.getBean(OutboxCommandExecutor.class));
        });
    }

    /**
     * 无补偿动作时补偿块整块跳过，Outbox 链路不受连坐影响。
     */
    @Test
    void shouldSkipCompensationWhenNoCompensableAction() {
        this.baseRunner.run(context -> {
            assertThat(context).hasSingleBean(OutboxCommandExecutor.class);
            assertThat(context).doesNotHaveBean(CompensableCommandExecutor.class);
            assertThat(context).doesNotHaveBean(CompensationActionRegistry.class);
        });
    }

    /**
     * 属性开关关闭补偿块时，注册表与装饰器均不装配。
     */
    @Test
    void shouldSkipCompensationWhenDisabledByProperty() {
        this.runnerWithAction()
                .withPropertyValues("pragmatic-ddd.compensation.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(OutboxCommandExecutor.class);
                    assertThat(context).doesNotHaveBean(CompensableCommandExecutor.class);
                    assertThat(context).doesNotHaveBean(CompensationActionRegistry.class);
                });
    }

    /**
     * 使用方声明同类型 Bean 时精确覆盖默认装饰器。
     */
    @Test
    void shouldBackOffWhenUserProvidesCompensableExecutor() {
        CompensableCommandExecutor customExecutor = mock(CompensableCommandExecutor.class);
        this.runnerWithAction()
                .withBean(CompensableCommandExecutor.class, () -> customExecutor)
                .run(context -> {
                    assertThat(context).hasSingleBean(CompensableCommandExecutor.class);
                    assertThat(context.getBean(CompensableCommandExecutor.class)).isSameAs(customExecutor);
                });
    }

    private ApplicationContextRunner runnerWithAction() {
        return this.baseRunner.withBean(TestReserveAction.class, TestReserveAction::new);
    }

    /**
     * 测试用聚合根：装配用例只取类型信息，不参与任何业务行为。
     */
    static class TestAggregate extends AggregateRoot<Long> {

        @Override
        protected BrokenRuleRegistry brokenRuleRegistry() {
            return null;
        }

        @Override
        protected OperationRegistry operationRegistry() {
            return null;
        }
    }

    /**
     * 测试用外部需求：只提供注册表索引所需的需求类型与编码。
     */
    record ReserveRequirement(String sku) implements IExternalRequirement {

        @Override
        public String code() {
            return "inventory.reserve";
        }
    }

    /**
     * 测试用可补偿动作：装配测试只依赖其类型信息，不执行任何正向、回写与补偿逻辑。
     */
    static class TestReserveAction implements ICompensableAction<TestAggregate, ReserveRequirement, String> {

        @Override
        public Class<ReserveRequirement> requirementType() {
            return ReserveRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, ReserveRequirement requirement) {
            return requirement.sku();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 装配测试不涉及回写聚合
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String result) {
            // 装配测试不涉及逆向补偿
        }
    }
}
