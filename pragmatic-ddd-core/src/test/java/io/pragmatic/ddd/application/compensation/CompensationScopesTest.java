package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿范围工厂测试：durable 与日志装配的一致性守卫，以及选项对范围的生效。
 *
 * @author wizard-lee
 */
class CompensationScopesTest {

    @Test
    void begin_inMemory_isCreated() {
        assertThat(CompensationScopes.begin(null, CompensationOptions.inMemory())).isNotNull();
    }

    @Test
    void begin_durable_withoutLog_throwsIllegalState() {
        assertThatThrownBy(() -> CompensationScopes.begin(null, CompensationOptions.durableMode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ICompensationLog");
    }

    @Test
    void begin_durable_withLog_succeeds() {
        ICompensationLog log = new InMemoryCompensationLog();

        assertThat(CompensationScopes.begin(log, CompensationOptions.durableMode())).isNotNull();
    }

    @Test
    void begin_durable_writesWalAndConfirmsOnCommit() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        LocalCompensationScope scope = CompensationScopes.begin(log, CompensationOptions.durableMode());

        scope.execute(new RecordingAction("a", journal).command());
        scope.commit();
        scope.close();

        // durable 选项由此真正生效：WAL 落 PENDING、正向成功与终态 CONFIRMED 均已写入日志
        assertThat(journal).containsExactly(
                "record:TestAggregate:1:a:PENDING",
                "execute:a",
                "markExecuted:TestAggregate:1:a",
                "markConfirmed:TestAggregate:1:a");
    }

    @Test
    void begin_inMemory_withLog_staysInMemory() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        LocalCompensationScope scope = CompensationScopes.begin(log, CompensationOptions.inMemory());

        scope.execute(new RecordingAction("a", journal).command());
        scope.commit();
        scope.close();

        // 装配了日志但选项为内存模式：不写任何日志
        assertThat(journal).containsExactly("execute:a");
        assertThat(log.get("TestAggregate:1:a")).isNull();
    }
}
