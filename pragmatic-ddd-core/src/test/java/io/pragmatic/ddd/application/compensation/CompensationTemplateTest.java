package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿包裹模板测试：成功提交、异常逆序补偿后重抛、close 异常隔离。
 *
 * @author wizard-lee
 */
class CompensationTemplateTest {

    @Test
    void run_success_commitsWithoutCompensation() {
        List<String> journal = new ArrayList<>();

        CompensationTemplate.run(new LocalCompensationManager(),
                scope -> scope.execute(new RecordingAction("a", journal).command()));

        assertThat(journal).containsExactly("execute:a");
    }

    @Test
    void call_returnsValue() {
        String result = CompensationTemplate.call(new LocalCompensationManager(),
                scope -> scope.execute(new RecordingAction("a", new ArrayList<>()).command()));

        assertThat(result).isEqualTo("result-a");
    }

    @Test
    void call_commandThrows_compensatesAndRethrowsOriginal() {
        List<String> journal = new ArrayList<>();
        IllegalStateException original = new IllegalStateException("boom");

        assertThatThrownBy(() -> CompensationTemplate.call(new LocalCompensationManager(), scope -> {
            scope.execute(new RecordingAction("a", journal).command());
            throw original;
        })).isSameAs(original);

        assertThat(journal).containsExactly("execute:a", "compensate:a");
    }

    @Test
    void closeThrowing_doesNotMaskOriginalException() {
        IllegalStateException original = new IllegalStateException("boom");
        ICompensationScope scope = new ThrowingCloseScope();

        assertThatThrownBy(() -> CompensationTemplate.run(scope, ignored -> {
            throw original;
        })).isSameAs(original);
    }

    @Test
    void closeThrowing_doesNotMaskCompensationFailedException() {
        ICompensationScope scope = new FailingRollbackScope();

        assertThatThrownBy(() -> CompensationTemplate.run(scope, ignored -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(CompensationFailedException.class);
    }

    @Test
    void closeThrowing_onSuccess_isSwallowed() {
        ICompensationScope scope = new ThrowingCloseScope();

        CompensationTemplate.run(scope, ignored -> {
        });
    }

    /** 关闭即抛异常的补偿范围，用于验证模板的 closeQuietly 兜底。 */
    private static class ThrowingCloseScope implements ICompensationScope {

        @Override
        public <A extends AggregateRoot<?>, REQ extends IExternalRequirement, R> R execute(
                CompensationCommand<A, REQ, R> command) {
            return command.execute();
        }

        @Override
        public void commit() {
        }

        @Override
        public void rollback(Throwable cause) {
        }

        @Override
        public void close() {
            throw new IllegalStateException("close failed");
        }
    }

    /** 补偿失败且关闭也抛异常的范围：验证 CompensationFailedException 不被 close 异常覆盖。 */
    private static final class FailingRollbackScope extends ThrowingCloseScope {

        @Override
        public void rollback(Throwable cause) {
            throw new CompensationFailedException(List.of(new CompensationFailure("a", cause)), cause);
        }
    }
}
