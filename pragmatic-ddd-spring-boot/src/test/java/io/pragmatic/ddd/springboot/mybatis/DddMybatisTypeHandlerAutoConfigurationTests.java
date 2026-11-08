package io.pragmatic.ddd.springboot.mybatis;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerAssembler;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerDeclaration;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 复杂类型装配自动配置测试：锁定「多份声明合并为单上下文」「无声明不装配」「业务自定义即退让」
 * 「显式方言优先」与「声明冲突启动失败」五条不变式。
 *
 * @author wizard-lee
 */
@DisplayName("复杂类型装配自动配置")
class DddMybatisTypeHandlerAutoConfigurationTests {

    enum Status implements IEnumValue<Integer, Status> {
        ACTIVE(1, "有效");

        private final int code;
        private final String label;

        Status(int code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    enum Channel implements IEnumValue<String, Channel> {
        APP("app", "应用");

        private final String code;
        private final String label;

        Channel(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DddMybatisTypeHandlerAutoConfiguration.class));

    @Test
    @DisplayName("多份声明合并为唯一上下文")
    void mergesDeclarationsIntoSingleContext() {
        this.runner
                .withBean("orderDeclaration", TypeHandlerDeclaration.class,
                        () -> TypeHandlerDeclaration.of(Map.of(Status.class, EnumRule.CODE), List.of(), List.of()))
                .withBean("memberDeclaration", TypeHandlerDeclaration.class,
                        () -> TypeHandlerDeclaration.of(Map.of(Channel.class, EnumRule.NAME), List.of(), List.of()))
                .run(context -> {
                    assertThat(context).hasSingleBean(TypeHandlerContext.class);
                    assertThat(context.getBean(TypeHandlerContext.class).enumRules())
                            .containsEntry(Status.class, EnumRule.CODE)
                            .containsEntry(Channel.class, EnumRule.NAME);
                });
    }

    @Test
    @DisplayName("容器无任何声明时不产出上下文")
    void noDeclarationProducesNoContext() {
        this.runner.run(context -> assertThat(context).doesNotHaveBean(TypeHandlerContext.class));
    }

    @Test
    @DisplayName("业务自定义上下文时自动配置退让")
    void backsOffWhenUserProvidesContext() {
        this.runner
                .withBean(TypeHandlerDeclaration.class,
                        () -> TypeHandlerDeclaration.of(Map.of(Status.class, EnumRule.CODE), List.of(), List.of()))
                .withBean("customTypeHandlerContext", TypeHandlerContext.class,
                        () -> TypeHandlerAssembler.assemble(
                                List.of(TypeHandlerDeclaration.empty()), JdbcJsonValue.DEFAULT))
                .run(context -> {
                    assertThat(context).hasSingleBean(TypeHandlerContext.class);
                    assertThat(context.getBean(TypeHandlerContext.class).enumRules()).isEmpty();
                });
    }

    @Test
    @DisplayName("业务显式声明的方言适配器优先于数据源探测")
    void explicitJdbcJsonValueWins() {
        JdbcJsonValue explicit = json -> json;

        this.runner
                .withBean(TypeHandlerDeclaration.class,
                        () -> TypeHandlerDeclaration.of(Map.of(Status.class, EnumRule.CODE), List.of(), List.of()))
                .withBean(JdbcJsonValue.class, () -> explicit)
                .run(context -> assertThat(context.getBean(TypeHandlerContext.class).jdbcJsonValue())
                        .isSameAs(explicit));
    }

    @Test
    @DisplayName("声明冲突时启动失败")
    void conflictingDeclarationsFailStartup() {
        this.runner
                .withBean("firstDeclaration", TypeHandlerDeclaration.class,
                        () -> TypeHandlerDeclaration.of(Map.of(Status.class, EnumRule.CODE), List.of(), List.of()))
                .withBean("secondDeclaration", TypeHandlerDeclaration.class,
                        () -> TypeHandlerDeclaration.of(Map.of(Status.class, EnumRule.NAME), List.of(), List.of()))
                .run(context -> assertThat(context).hasFailed());
    }
}
