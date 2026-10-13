package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿编排器工厂测试：durable 与日志装配的一致性守卫。
 *
 * @author wizard-lee
 */
class LocalCompensationManagerTest {

    @Test
    void begin_default_isInMemory() {
        assertThat(new LocalCompensationManager().begin()).isNotNull();
    }

    @Test
    void begin_durable_withoutLog_throwsIllegalState() {
        ICompensationManager manager = new LocalCompensationManager();

        assertThatThrownBy(() -> manager.begin(CompensationOptions.durableMode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ICompensationLog");
    }

    @Test
    void begin_durable_withLog_succeeds() {
        ICompensationLog log = new InMemoryCompensationLog();
        ICompensationManager manager = new LocalCompensationManager(log);

        assertThat(manager.begin(CompensationOptions.durableMode())).isNotNull();
    }

    @Test
    void begin_defaultOptionsDurable_appliesDurableToImplicitScope() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationManager manager = new LocalCompensationManager(
                log,
                CompensationOptions.durableMode());

        ICompensationScope scope = manager.begin();
        scope.execute(new RecordingAction("a", journal).command());
        scope.commit();
        scope.close();

        // 默认选项由构造器生效：WAL 落 PENDING、正向成功与终态 CONFIRMED 均已写入日志
        assertThat(journal).containsExactly(
                "record:TestAggregate:1:a:PENDING",
                "execute:a",
                "markExecuted:TestAggregate:1:a",
                "markConfirmed:TestAggregate:1:a");
    }

    @Test
    void begin_logWithoutDefaultOptions_staysInMemory() {
        List<String> journal = new ArrayList<>();
        InMemoryCompensationLog log = new InMemoryCompensationLog(journal);
        ICompensationScope scope = new LocalCompensationManager(log).begin();

        scope.execute(new RecordingAction("a", journal).command());
        scope.commit();
        scope.close();

        // 单参构造器语义不变：装配了日志但默认仍是内存模式，不写任何日志
        assertThat(journal).containsExactly("execute:a");
        assertThat(log.get("TestAggregate:1:a")).isNull();
    }
}
