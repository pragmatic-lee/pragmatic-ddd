package io.pragmatic.ddd.springboot.compensation;

import io.pragmatic.ddd.application.compensation.relay.CompensationRelay;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 补偿中继的"就绪后启动"生命周期组件：容器就绪后按固定间隔挂载 {@link CompensationRelay#pollOnce()}。
 *
 * <p>phase 取 {@code Integer.MAX_VALUE - 512}，晚于 {@code DddStartupLifecycle}（{@code -1024}）启动、
 * 早于它停止：事件收发与 Outbox 兜底轮询先就绪，崩溃记录的回抢最后挂上；关闭时中继先停，避免向尚未就绪的下游投递。</p>
 *
 * <p>调度器回收交由 Bean 的 {@code destroyMethod = "shutdown"}，本组件 stop 不做重复关闭。</p>
 *
 * @author wizard-lee
 */
public class CompensationRelayLifecycle implements SmartLifecycle {

    private final CompensationRelay relay;
    private final ScheduledExecutorService scheduler;
    private final Duration interval;

    private volatile boolean running = false;

    /**
     * 以中继、调度器与轮询间隔构造生命周期组件。
     *
     * @param relay     补偿中继
     * @param scheduler 轮询调度器（由容器负责回收）
     * @param interval  轮询间隔，必须为正
     */
    public CompensationRelayLifecycle(CompensationRelay relay,
                                      ScheduledExecutorService scheduler,
                                      Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("pragmatic-ddd.compensation.relay-poll-interval must be positive, but was: "
                    + interval);
        }
        this.relay = relay;
        this.scheduler = scheduler;
        this.interval = interval;
    }

    /**
     * 应用就绪后挂载轮询任务。
     */
    @Override
    public void start() {
        long periodMillis = this.interval.toMillis();
        this.scheduler.scheduleWithFixedDelay(this.relay::pollOnce, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
        this.running = true;
    }

    /**
     * 停止轮询：只置运行标记，已在途的一轮跑完，不再提交新轮询。
     */
    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    /**
     * 取比 {@code DddStartupLifecycle} 更高的 phase：晚于事件收发与 Outbox 轮询启动，关闭时先于它们停止。
     *
     * @return 启动阶段
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 512;
    }
}
