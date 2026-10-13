package io.pragmatic.ddd.springboot.lifecycle;

import io.pragmatic.ddd.application.outbox.OutboxRelay;
import io.pragmatic.ddd.event.spi.IEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

/**
 * 统一的“就绪后启动”生命周期组件：待容器全部单例初始化完成后，依次拉起事件管理器与 Outbox 兜底轮询。
 * 取代示例中分散的多个 {@code ApplicationRunner}，把启动时机与顺序收敛到框架内部。
 *
 * <p>停止动作交由各自的 {@code destroyMethod}（事件管理器 shutdown、轮询调度器 shutdown）负责，
 * 本组件 stop 不做重复关闭，避免双重回收。</p>
 *
 * @author wizard-lee
 */
public class DddStartupLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DddStartupLifecycle.class);

    private final ObjectProvider<IEventManager> eventManagerProvider;
    private final ObjectProvider<OutboxRelay> outboxRelayProvider;

    private volatile boolean running = false;

    /**
     * 以两类可选协作者构造生命周期组件。
     *
     * @param eventManagerProvider  事件管理器提供者（无总线时为空）
     * @param outboxRelayProvider   Outbox 兜底轮询提供者（未启用 outbox 时为空）
     */
    public DddStartupLifecycle(ObjectProvider<IEventManager> eventManagerProvider,
                               ObjectProvider<OutboxRelay> outboxRelayProvider) {
        this.eventManagerProvider = eventManagerProvider;
        this.outboxRelayProvider = outboxRelayProvider;
    }

    @Override
    public void start() {
        this.eventManagerProvider.ifAvailable(manager -> {
            manager.start();
            log.info("DDD event manager started");
        });
        this.outboxRelayProvider.ifAvailable(relay -> {
            relay.start();
            log.info("DDD outbox relay started");
        });
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    /**
     * 取较高 phase：在所有普通单例与更早 phase 的生命周期组件就绪后再启动。
     *
     * @return 启动阶段
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1024;
    }
}
