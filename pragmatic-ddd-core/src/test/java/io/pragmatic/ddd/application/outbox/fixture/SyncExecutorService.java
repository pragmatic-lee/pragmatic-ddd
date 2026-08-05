package io.pragmatic.ddd.application.outbox.fixture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 同步执行器测试夹具：submit 的任务直接在当前线程立即执行（返回已完成 Future），
 * 保证 EagerOutboxPublisher 的异步推送可以在单测内做确定性断言。
 *
 * @author wizard-lee
 */
public class SyncExecutorService extends AbstractExecutorService {

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
        command.run();
    }
}
