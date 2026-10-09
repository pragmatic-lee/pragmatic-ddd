package io.pragmatic.ddd.application.outbox.fixture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 拒绝型执行器测试夹具：模拟有界线程池饱和或已关闭，execute 直接抛 RejectedExecutionException，
 * 用于验证 EagerOutboxPublisher 提交后路径不向调用方抛异常。
 *
 * @author wizard-lee
 */
public class RejectedExecutorService extends AbstractExecutorService {

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
        return true;
    }

    @Override
    public boolean isTerminated() {
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public void execute(Runnable command) {
        throw new RejectedExecutionException("pool saturated or shutdown");
    }
}
