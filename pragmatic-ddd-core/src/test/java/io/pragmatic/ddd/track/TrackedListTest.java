package io.pragmatic.ddd.track;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TrackedList 单元测试（自 base/test1 迁入，迁 JUnit5 + AssertJ）。
 *
 * 注意：TrackedList 构造器按引用持有基线集合并就地变更（设计用于 MyBatis 懒加载代理），
 * 因此传入的基线集合必须是可变 List（这里用 {@link #items} 辅助方法包装）。
 */
class TrackedListTest {

    // ===== 测试辅助 =====

    /**
     * 包级可见测试夹具：实现 ITrackable，按 id 判定相等（非 BrokenRuleRegistry 子类，无需 public）。
     *
     * @author wizard-lee
     */
    static class TestItem implements ITrackable<String> {
        final String id;
        final String label;

        TestItem(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestItem other)) return false;
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "Item(" + id + "," + label + ")";
        }
    }

    static TestItem item(String id, String label) {
        return new TestItem(id, label);
    }

    /** 返回可变 List，供 TrackedList 构造器按引用持有并就地变更。 */
    @SafeVarargs
    static List<TestItem> items(TestItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    // ===== 构造器 + 变更 =====

    @Test
    void emptyConstructor_appendThenClearAndAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>();

        list.append(item("1", "a"));
        list.append(item("2", "b"));

        assertThat(list.getAllItems()).hasSize(2);
        assertThat(list.getAppendedItems()).hasSize(2);

        list.clearAndAppend(items(item("3", "c"), item("4", "d")));

        assertThat(list.getAllItems()).hasSize(2);
        assertThat(list.getAppendedItems()).hasSize(2);
    }

    @Test
    void initWithList_appendThenGetAllItems() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.append(item("3", "c"));
        list.append(item("4", "d"));

        assertThat(list.getAllItems()).hasSize(4);
        assertThat(list.getAppendedItems()).hasSize(2);
    }

    @Test
    void removeAll_movesInitToRemoveAndClearsAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.append(item("3", "c"));
        list.removeAll();
        list.append(item("4", "d"));

        assertThat(list.getRemovedItems()).hasSize(2);
        assertThat(list.getAppendedItems()).hasSize(1);
        assertThat(list.getAllItems()).hasSize(1);
    }

    @Test
    void removeItems_byIdRemovesFromInitAndAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.append(item("1", "a_dup")); // append 中也有 id=1
        List<TestItem> removed = list.removeItems(i -> i.id().equals("1"));

        // append 中的 id=1 直接丢弃（无需删）；init 中的 id=1 移入 removeList
        assertThat(removed).hasSize(2);
        assertThat(list.getRemovedItems()).hasSize(1);
        assertThat(list.getRemovedItems().get(0).id).isEqualTo("1");
        assertThat(list.getAllItems()).hasSize(1); // 仅剩 init 中的 id=2
    }

    @Test
    void update_replacesInitItemAndAppendsNew() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b"), item("3", "c")));

        // 用新对象替换 id=2（只要求 id() 命中，label 随意）
        list.update(item("2", "whatever"), item("2", "b_updated"));

        assertThat(list.getRemovedItems()).hasSize(1);
        assertThat(list.getRemovedItems().get(0).label).isEqualTo("b");

        assertThat(list.getAppendedItems()).hasSize(1);
        assertThat(list.getAppendedItems().get(0).label).isEqualTo("b_updated");

        assertThat(list.getAllItems()).hasSize(3);
    }

    @Test
    void update_nonExistingId_throwsIllegalArgumentException() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a")));
        assertThatThrownBy(() -> list.update(item("99", "ghost"), item("99", "nobody")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearAndAppend_movesInitToRemoveAndAppendsNew() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.clearAndAppend(items(item("3", "x"), item("4", "y")));

        assertThat(list.getRemovedItems()).hasSize(2);
        assertThat(list.getAllItems()).hasSize(2); // 仅含 append 项
        assertThat(list.getAppendedItems()).hasSize(2);
    }

    // ===== 读 API =====

    @Test
    void getInitItems_returnsBaselineSnapshot() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));
        list.append(item("3", "c"));

        assertThat(list.getInitItems()).extracting(i -> i.id).containsExactly("1", "2");
    }

    @Test
    void append_batchAddsAllToAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a")));

        list.append(items(item("2", "b"), item("3", "c")));

        assertThat(list.getAppendedItems()).hasSize(2);
        assertThat(list.getAppendedItems()).extracting(i -> i.id).containsExactly("2", "3");
    }

    // ===== 不可变契约 =====

    @Test
    void getAppendedItems_isImmutable() {
        TrackedList<TestItem, String> list = new TrackedList<>();
        list.append(item("1", "a"));
        assertThatThrownBy(() -> list.getAppendedItems().add(item("2", "b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getRemovedItems_isImmutable() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a")));
        list.removeAll();
        assertThatThrownBy(() -> list.getRemovedItems().add(item("1", "a")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllItems_isImmutable() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a")));
        assertThatThrownBy(() -> list.getAllItems().add(item("2", "b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getAllItems_preservesInitThenAppendOrder() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));
        list.append(item("3", "c"));
        list.append(item("4", "d"));

        assertThat(list.getAllItems()).extracting(i -> i.id).containsExactly("1", "2", "3", "4");
    }

    // ===== removeItems 分支 =====

    @Test
    void removeItems_onlyInAppend_dropsWithoutRemove() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));
        list.append(item("9", "extra")); // 仅 append，不在 init

        List<TestItem> removed = list.removeItems(i -> i.id().equals("9"));

        assertThat(removed).hasSize(1);
        assertThat(list.getRemovedItems()).isEmpty();
        assertThat(list.getAllItems()).extracting(i -> i.id).containsExactly("1", "2");
    }

    @Test
    void removeItems_onlyInInit_movesToRemove() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));
        list.append(item("3", "c"));

        List<TestItem> removed = list.removeItems(i -> i.id().equals("1"));

        assertThat(removed).hasSize(1);
        assertThat(list.getRemovedItems()).hasSize(1);
        assertThat(list.getAllItems()).extracting(i -> i.id).containsExactly("2", "3");
    }

    @Test
    void removeItems_noMatch_returnsEmptyAndUnchanged() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));
        list.append(item("3", "c"));

        List<TestItem> removed = list.removeItems(i -> false);

        assertThat(removed).isEmpty();
        assertThat(list.getRemovedItems()).isEmpty();
        assertThat(list.getAllItems()).extracting(i -> i.id).containsExactly("1", "2", "3");
    }

    @Test
    void update_thenAppendSameId_staysInAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b"), item("3", "c")));

        list.update(item("2", "whatever"), item("2", "b2"));
        list.append(item("2", "b3")); // update 后再 append 同 id

        assertThat(list.getRemovedItems()).hasSize(1);
        assertThat(list.getAppendedItems()).hasSize(2);
        assertThat(list.getAllItems()).extracting(i -> i.id).containsExactly("1", "3", "2", "2");
    }
}
