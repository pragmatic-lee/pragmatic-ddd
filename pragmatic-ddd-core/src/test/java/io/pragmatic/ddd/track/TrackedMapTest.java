package io.pragmatic.ddd.track;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TrackedMap 单元测试（自 base/test1 迁入，迁 JUnit5 + AssertJ）。
 */
class TrackedMapTest {

    // ===== 构造器与基线 =====

    @Test
    void emptyConstructor() {
        TrackedMap<String, String> map = new TrackedMap<>();

        map.put("a", "1");
        assertThat(map.getInsertedEntries()).hasSize(1);
    }

    @Test
    void initWithMap() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        assertThat(map.getInitEntries()).hasSize(2);
    }

    // ===== 变更 + 差量 =====

    @Test
    void putNewKey_isInserted() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "2");

        assertThat(map.getInsertedEntries()).hasSize(1);
        assertThat(map.getInsertedEntries().get("b")).isEqualTo("2");
        assertThat(map.getUpdatedEntries()).hasSize(0);
        assertThat(map.getAllEntries()).hasSize(2);
    }

    @Test
    void putExistingKey_isUpdated() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("a", "11"); // 替换已有 key → UPDATE

        assertThat(map.getUpdatedEntries()).hasSize(1);
        assertThat(map.getUpdatedEntries().get("a")).isEqualTo("11");
        assertThat(map.getInsertedEntries()).hasSize(0);
        assertThat(map.getAllEntries()).hasSize(2);
        assertThat(map.getAllEntries().get("a")).isEqualTo("11");
    }

    @Test
    void removeInitKey_isRemoved() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.remove("a");

        assertThat(map.getRemovedEntries()).hasSize(1);
        assertThat(map.getRemovedEntries().get("a")).isEqualTo("1");
        assertThat(map.getAllEntries()).hasSize(1);
        assertThat(map.getAllEntries()).doesNotContainKey("a");
    }

    @Test
    void removeThenPutSameKey_isReinserted() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.remove("a");
        assertThat(map.getRemovedEntries()).hasSize(1);

        map.put("a", "1"); // 重新 PUT 同一 key → 撤销删除，视为新增
        assertThat(map.getRemovedEntries()).hasSize(0);
        assertThat(map.getInsertedEntries()).hasSize(1);
        assertThat(map.getInsertedEntries().get("a")).isEqualTo("1");
    }

    @Test
    void removeOnlyPutKey_notInRemoved() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "2");  // 新增
        map.remove("b");     // 仅刚 put，未持久化 → 直接从 putMap 移除，不进 removeKeys

        assertThat(map.getRemovedEntries()).hasSize(0);
        assertThat(map.getInsertedEntries()).hasSize(0);
        assertThat(map.getAllEntries()).hasSize(1);
    }

    @Test
    void clear_allInitKeysRemoved() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.clear();

        assertThat(map.getRemovedEntries()).hasSize(2);
        assertThat(map.getAllEntries()).hasSize(0);
    }

    @Test
    void getRetainedEntries_untouchedOnly() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        init.put("c", "3");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("a", "11"); // updated
        map.remove("b");     // removed

        assertThat(map.getRetainedEntries()).hasSize(1);
        assertThat(map.getRetainedEntries()).containsKey("c");
    }

    // ===== removeEntry(Predicate) =====

    @Test
    void removeEntry_byPredicate() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        init.put("c", "3");
        TrackedMap<String, String> map = new TrackedMap<>(new LinkedHashMap<>(init));

        // 按 init 值命中 a、c → 二者移入 removeKeys（发 DELETE）
        map.removeEntry(v -> v.equals("1") || v.equals("3"));

        assertThat(map.getRemovedEntries()).containsOnlyKeys("a", "c");
        assertThat(map.getRemovedEntries().get("a")).isEqualTo("1");
        assertThat(map.getRemovedEntries().get("c")).isEqualTo("3");
        // 逻辑视图仅剩未被移除的 b
        assertThat(map.getAllEntries()).containsOnlyKeys("b");
    }

    // ===== rowIdOf =====

    @Test
    void rowIdOf_returnsKey() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        assertThat(map.rowIdOf("a", "1")).isEqualTo("a");
    }

    // ===== 不可变读 API =====

    @Test
    void getInitEntries_isImmutable() {
        TrackedMap<String, String> map = new TrackedMap<>();
        assertThatThrownBy(() -> map.getInitEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getInsertedEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.put("b", "2");
        assertThatThrownBy(() -> map.getInsertedEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getRemovedEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.remove("a");
        assertThatThrownBy(() -> map.getRemovedEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getRemoveKeys_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        assertThatThrownBy(() -> map.getRemoveKeys().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
