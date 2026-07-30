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

    // ===== 新增覆盖 =====

    @Test
    void getPutEntries_returnsRawPutMap() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "2");  // 新增
        map.put("a", "11"); // 替换

        assertThat(map.getPutEntries()).hasSize(2);
        assertThat(map.getPutEntries()).containsEntry("a", "11").containsEntry("b", "2");
        assertThat(map.getInsertedEntries()).containsOnlyKeys("b");
        assertThat(map.getUpdatedEntries()).containsOnlyKeys("a");
    }

    @Test
    void putAll_addsAllEntriesAsPut() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        Map<String, String> batch = new LinkedHashMap<>();
        batch.put("b", "2");
        batch.put("a", "11"); // 已有 key
        map.putAll(batch);

        assertThat(map.getInsertedEntries()).containsOnlyKeys("b");
        assertThat(map.getUpdatedEntries()).containsOnlyKeys("a");
        assertThat(map.getPutEntries()).containsEntry("b", "2").containsEntry("a", "11");
    }

    @Test
    void equals_sameState_isEqual() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");

        TrackedMap<String, String> m1 = new TrackedMap<>(new LinkedHashMap<>(init));
        m1.put("b", "2");

        TrackedMap<String, String> m2 = new TrackedMap<>(new LinkedHashMap<>(init));
        m2.put("b", "2");

        assertThat(m1).isEqualTo(m2);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");

        TrackedMap<String, String> m1 = new TrackedMap<>(new LinkedHashMap<>(init));
        m1.put("b", "2");

        TrackedMap<String, String> m2 = new TrackedMap<>(new LinkedHashMap<>(init));
        m2.put("b", "2");

        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }

    @Test
    void equals_differentState_isNotEqual() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");

        TrackedMap<String, String> m1 = new TrackedMap<>(new LinkedHashMap<>(init));
        m1.put("b", "2");

        TrackedMap<String, String> m2 = new TrackedMap<>(new LinkedHashMap<>(init));
        m2.remove("a"); // removeKeys 不同

        assertThat(m1).isNotEqualTo(m2);
    }

    @Test
    void removeEntry_byPutValue_removesFromPutMap() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "2"); // 仅 put，未持久化
        map.removeEntry(v -> v.equals("2"));

        assertThat(map.getPutEntries()).isEmpty();
        assertThat(map.getInsertedEntries()).isEmpty();
        assertThat(map.getRemovedEntries()).isEmpty();
        assertThat(map.getAllEntries()).containsOnlyKeys("a");
    }

    @Test
    void removeEntry_noMatch_noOp() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.removeEntry(v -> v.equals("zzz"));

        assertThat(map.getInitEntries()).containsOnlyKeys("a");
        assertThat(map.getPutEntries()).isEmpty();
        assertThat(map.getRemovedEntries()).isEmpty();
    }

    @Test
    void clear_thenPut_reinsertsAsInsert() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.clear();
        map.put("a", "1"); // 原基线 key 在 clear 后重 PUT → 视为 reinsert

        // 仅重新 PUT 的 a 撤销删除（进 inserted）；未被重新 PUT 的 b 仍保留在 removeKeys
        assertThat(map.getRemovedEntries()).containsOnlyKeys("b");
        assertThat(map.getInsertedEntries()).containsOnlyKeys("a");
        assertThat(map.getAllEntries()).containsOnlyKeys("a");
    }

    @Test
    void getAllEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.put("b", "2");
        assertThatThrownBy(() -> map.getAllEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getPutEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.put("b", "2");
        assertThatThrownBy(() -> map.getPutEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getUpdatedEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.put("a", "11");
        assertThatThrownBy(() -> map.getUpdatedEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getRetainedEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        init.put("c", "3");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.put("a", "11");
        map.remove("b");
        assertThatThrownBy(() -> map.getRetainedEntries().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllEntries_containsInitAndPutKeys() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("c", "3");
        map.put("d", "4");

        // 逻辑视图为基线 + putMap 的并集：仅断言成员（union 顺序非实现契约）
        assertThat(map.getAllEntries()).containsEntry("a", "1").containsEntry("b", "2")
                .containsEntry("c", "3").containsEntry("d", "4");
    }

    @Test
    void getInitEntries_containsBaselineEntries() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        init.put("c", "3");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        // 仅断言成员（Map.copyOf 支撑的只读视图不保证迭代顺序）
        assertThat(map.getInitEntries()).containsEntry("a", "1").containsEntry("b", "2").containsEntry("c", "3");
    }
}
