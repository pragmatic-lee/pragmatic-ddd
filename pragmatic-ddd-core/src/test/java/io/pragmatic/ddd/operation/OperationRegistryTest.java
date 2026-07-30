package io.pragmatic.ddd.operation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OperationRegistry 注册表基类测试。
 *
 * @author wizard-lee
 */
class OperationRegistryTest {

    @Test
    void constructor_registersBuiltinNewAndDelete() {
        SampleRegistry registry = new SampleRegistry();

        assertThat(registry.operations())
                .containsKeys(OperationRegistry.NEW.code(), OperationRegistry.DELETE.code());
    }

    @Test
    void constructor_autoRegistersSubclassStaticFields() {
        SampleRegistry registry = new SampleRegistry();

        assertThat(registry.operations())
                .containsEntry(SampleRegistry.A.code(), SampleRegistry.A)
                .containsEntry(SampleRegistry.B.code(), SampleRegistry.B)
                .containsEntry(SampleRegistry.C.code(), SampleRegistry.C);
    }

    @Test
    void operations_returnsUnmodifiableView() {
        SampleRegistry registry = new SampleRegistry();
        Map<String, EntityOperation> operations = registry.operations();

        assertThatThrownBy(() -> operations.put("X", EntityOperation.of("X")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void register_duplicateCode_lastOneWins() {
        DuplicateCodeRegistry registry = new DuplicateCodeRegistry();

        assertThat(registry.operations()).containsKey("dup");
        assertThat(registry.operations().get("dup").description()).isEqualTo("后注册");
    }

    /**
     * 同 code 重复注册的测试夹具，字段声明顺序决定注册顺序。
     */
    static class DuplicateCodeRegistry extends OperationRegistry {
        public static final EntityOperation FIRST = EntityOperation.of("dup", "先注册");
        public static final EntityOperation SECOND = EntityOperation.of("dup", "后注册");
    }
}
