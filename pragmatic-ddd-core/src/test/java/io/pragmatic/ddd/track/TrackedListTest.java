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

    // ===== equals 兜底模式 =====

    @Test
    void initIsEmptyTrackedList() {
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
    void initNotEmptyTrackedList() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.append(item("3", "c"));
        list.append(item("4", "d"));

        assertThat(list.getAllItems()).hasSize(4);
        assertThat(list.getAppendedItems()).hasSize(2);
    }

    @Test
    void testRemoveAll() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.append(item("3", "c"));
        list.removeAll();
        list.append(item("4", "d"));

        assertThat(list.getRemovedItems()).hasSize(2);
        assertThat(list.getAppendedItems()).hasSize(1);
        assertThat(list.getAllItems()).hasSize(1);
    }

    @Test
    void testRemoveItems() {
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
    void testUpdate() {
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
    void update_withNonExistingId_throws() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a")));
        assertThatThrownBy(() -> list.update(item("99", "ghost"), item("99", "nobody")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testClearAndAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>(items(item("1", "a"), item("2", "b")));

        list.clearAndAppend(items(item("3", "x"), item("4", "y")));

        assertThat(list.getRemovedItems()).hasSize(2);
        assertThat(list.getAllItems()).hasSize(2); // 仅含 append 项
        assertThat(list.getAppendedItems()).hasSize(2);
    }
}
