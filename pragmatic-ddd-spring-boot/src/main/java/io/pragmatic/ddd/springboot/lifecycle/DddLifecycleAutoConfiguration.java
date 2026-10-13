package io.pragmatic.ddd.springboot.lifecycle;

import io.pragmatic.ddd.application.outbox.OutboxRelay;
import io.pragmatic.ddd.event.spi.IEventManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 装配统一的“就绪后启动”生命周期组件。
 * 事件管理器与 Outbox 兜底轮询均为可选协作者，缺一也能正常工作。
 *
 * @author wizard-lee
 */
@AutoConfiguration
public class DddLifecycleAutoConfiguration {

    /**
     * 生命周期组件：容器就绪后拉起事件收发与兜底轮询。
     *
     * @param eventManagerProvider 事件管理器提供者
     * @param outboxRelayProvider  Outbox 兜底轮询提供者
     * @return 生命周期组件
     */
    @Bean
    @ConditionalOnMissingBean(DddStartupLifecycle.class)
    public DddStartupLifecycle dddStartupLifecycle(ObjectProvider<IEventManager> eventManagerProvider,
                                                   ObjectProvider<OutboxRelay> outboxRelayProvider) {
        return new DddStartupLifecycle(eventManagerProvider, outboxRelayProvider);
    }
}
