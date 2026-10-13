package io.pragmatic.ddd.springboot.configuration;

import io.pragmatic.ddd.config.IConfigurationSource;
import io.pragmatic.ddd.springboot.bridge.EnvironmentConfigurationSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 装配框架配置端口 {@link IConfigurationSource} 的 Spring 实现。
 * 让 {@code ConfigurationBinder} 与各框架 {@code *Properties.bind(source)} 可直接读取
 * Spring 外部化配置，是使用方覆盖的第一优先装配点。
 *
 * @author wizard-lee
 */
@AutoConfiguration
public class DddConfigurationAutoConfiguration {

    /**
     * 以 Spring {@link Environment} 为后端提供配置源。
     *
     * @param environment Spring 环境
     * @return 配置源实现
     */
    @Bean
    @ConditionalOnMissingBean(IConfigurationSource.class)
    public IConfigurationSource dddConfigurationSource(Environment environment) {
        return new EnvironmentConfigurationSource(environment);
    }
}
