package io.pragmatic.ddd.application.outbox.fixture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 记录型调度器测试夹具：捕获 scheduleAtFixedRate 的入参而不真正周期性执行，用于断言 OutboxRelay#start 的装配。
 *
 * @author wizard-lee
 */
public class RecordingScheduledExecutorService extends AbstractExecutorService implements ScheduledExecutorService {

    private Runnable periodicTask;
    private long initialDelay;
    private long period;
    private TimeUnit unit;

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        this.periodicTask = command;
        this.initialDelay = initialDelay;
        this.period = period;
        this.unit = unit;
        return null;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public void shutdown() {
        // 测试夹具无需真实停机
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public void execute(Runnable command) {
        // 测试夹具不真正执行
    }

    public Runnable periodicTask() {
        return periodicTask;
    }

    public long initialDelay() {
        return initialDelay;
    }

    public long period() {
        return period;
    }

    public TimeUnit unit() {
        return unit;
    }
}
