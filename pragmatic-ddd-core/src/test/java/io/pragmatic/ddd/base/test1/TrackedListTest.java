package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.track.ITrackable;
import io.pragmatic.ddd.track.TrackedList;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lixiaojing10
 * @date 2021/9/2 10:05 下午
 */
public class TrackedListTest {

    // ===== 测试辅助 =====

    static class TestItem implements ITrackable<String> {
        final String id;
        final String label;

        TestItem(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String id() { return id; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestItem other)) return false;
            return id.equals(other.id);
        }

        @Override
        public int hashCode() { return id.hashCode(); }

        @Override
        public String toString() { return "Item(" + id + "," + label + ")"; }
    }

    static TestItem item(String id, String label) {
        return new TestItem(id, label);
    }

    // ===== equals 兜底模式 =====

    @Test
    public void initIsEmptyTrackedList() {
        TrackedList<TestItem, String> list = new TrackedList<>();

        list.append(item("1", "a"));
        list.append(item("2", "b"));

        Assert.assertEquals(2, list.getAllItems().size());
        Assert.assertEquals(2, list.getAppendedItems().size());

        list.clearAndAppend(List.of(item("3", "c"), item("4", "d")));

        Assert.assertEquals(2, list.getAllItems().size());
        Assert.assertEquals(2, list.getAppendedItems().size());
    }

    @Test
    public void initNotEmptyTrackedList() {
        TrackedList<TestItem, String> list = new TrackedList<>(
                List.of(item("1", "a"), item("2", "b")));

        list.append(item("3", "c"));
        list.append(item("4", "d"));

        Assert.assertEquals(4, list.getAllItems().size());
        Assert.assertEquals(2, list.getAppendedItems().size());
    }

    @Test
    public void testRemoveAll() {
        TrackedList<TestItem, String> list = new TrackedList<>(
                List.of(item("1", "a"), item("2", "b")));

        list.append(item("3", "c"));
        list.removeAll();
        list.append(item("4", "d"));

        Assert.assertEquals(2, list.getRemovedItems().size());
        Assert.assertEquals(1, list.getAppendedItems().size());
        Assert.assertEquals(1, list.getAllItems().size());
    }

    @Test
    public void testRemoveItems() {
        TrackedList<TestItem, String> list = new TrackedList<>(
                List.of(item("1", "a"), item("2", "b")));

        list.append(item("1", "a_dup")); // append 中也有 id=1
        List<TestItem> removed = list.removeItems(i -> i.id().equals("1"));

        // append 中的 id=1 直接丢弃（无需删）；init 中的 id=1 移入 removeList
        Assert.assertEquals(2, removed.size());
        Assert.assertEquals(1, list.getRemovedItems().size());
        Assert.assertEquals("1", list.getRemovedItems().get(0).id());
        Assert.assertEquals(1, list.getAllItems().size()); // 仅剩 init 中的 id=2
    }

    @Test
    public void testUpdate() {
        TrackedList<TestItem, String> list = new TrackedList<>(
                List.of(item("1", "a"), item("2", "b"), item("3", "c")));

        // 用新对象替换 id=2（只要求 id() 命中，label 随意）
        list.update(item("2", "whatever"), item("2", "b_updated"));

        Assert.assertEquals(1, list.getRemovedItems().size());
        Assert.assertEquals("b", list.getRemovedItems().get(0).label);

        Assert.assertEquals(1, list.getAppendedItems().size());
        Assert.assertEquals("b_updated", list.getAppendedItems().get(0).label);

        Assert.assertEquals(3, list.getAllItems().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void update_withNonExistingId_throws() {
        TrackedList<TestItem, String> list = new TrackedList<>(
                List.of(item("1", "a")));
        list.update(item("99", "ghost"), item("99", "nobody"));
    }

    @Test
    public void testClearAndAppend() {
        TrackedList<TestItem, String> list = new TrackedList<>(
                List.of(item("1", "a"), item("2", "b")));

        list.clearAndAppend(List.of(item("3", "x"), item("4", "y")));

        Assert.assertEquals(2, list.getRemovedItems().size());
        Assert.assertEquals(2, list.getAllItems().size()); // 仅含 append 项
        Assert.assertEquals(2, list.getAppendedItems().size());
    }
}
