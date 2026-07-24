package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.track.TrackedMap;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TrackedMap 单元测试：覆盖 put/remove 差量三类（inserted/updated/removed）。
 *
 * @author lixiaojing10
 * @date 2021/9/2 10:05 下午
 */
public class TrackedMapTest {

    // ===== 构造器与基线 =====

    @Test
    public void emptyConstructor() {
        TrackedMap<String, String> map = new TrackedMap<>();

        map.put("a", "1");
        Assert.assertEquals(1, map.getInsertedEntries().size());
    }

    @Test
    public void initWithMap() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        Assert.assertEquals(2, map.getInitEntries().size());
    }

    // ===== 变更 + 差量 =====

    @Test
    public void putNewKey_isInserted() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "2");

        Assert.assertEquals(1, map.getInsertedEntries().size());
        Assert.assertEquals("2", map.getInsertedEntries().get("b"));
        Assert.assertEquals(0, map.getUpdatedEntries().size());
        Assert.assertEquals(2, map.getAllEntries().size());
    }

    @Test
    public void putExistingKey_isUpdated() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("a", "11"); // 替换已有 key → UPDATE

        Assert.assertEquals(1, map.getUpdatedEntries().size());
        Assert.assertEquals("11", map.getUpdatedEntries().get("a"));
        Assert.assertEquals(0, map.getInsertedEntries().size());
        Assert.assertEquals(2, map.getAllEntries().size());
        Assert.assertEquals("11", map.getAllEntries().get("a"));
    }

    @Test
    public void removeInitKey_isRemoved() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.remove("a");

        Assert.assertEquals(1, map.getRemovedEntries().size());
        Assert.assertEquals("1", map.getRemovedEntries().get("a"));
        Assert.assertEquals(1, map.getAllEntries().size());
        Assert.assertFalse(map.getAllEntries().containsKey("a"));
    }

    @Test
    public void removeThenPutSameKey_isReinserted() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.remove("a");
        Assert.assertEquals(1, map.getRemovedEntries().size());

        map.put("a", "1"); // 重新 PUT 同一 key → 撤销删除，视为新增
        Assert.assertEquals(0, map.getRemovedEntries().size());
        Assert.assertEquals(1, map.getInsertedEntries().size());
        Assert.assertEquals("1", map.getInsertedEntries().get("a"));
    }

    @Test
    public void removeOnlyPutKey_notInRemoved() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "2");  // 新增
        map.remove("b");     // 仅刚 put，未持久化 → 直接从 putMap 移除，不进 removeKeys

        Assert.assertEquals(0, map.getRemovedEntries().size());
        Assert.assertEquals(0, map.getInsertedEntries().size());
        Assert.assertEquals(1, map.getAllEntries().size());
    }

    @Test
    public void clear_allInitKeysRemoved() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.clear();

        Assert.assertEquals(2, map.getRemovedEntries().size());
        Assert.assertEquals(0, map.getAllEntries().size());
    }

    @Test
    public void getRetainedEntries_untouchedOnly() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "2");
        init.put("c", "3");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("a", "11"); // updated
        map.remove("b");     // removed

        Assert.assertEquals(1, map.getRetainedEntries().size());
        Assert.assertTrue(map.getRetainedEntries().containsKey("c"));
    }

    // ===== removeEntry(Predicate) =====

    @Test
    public void removeEntry_byPredicate() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        init.put("b", "B");
        init.put("c", "3");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        map.put("b", "B_v2"); // b 在 putMap
        map.removeEntry(v -> v.equals("1") || v.equals("B_v2"));

        // b 的 value 在 putMap 是 "B_v2"，匹配 Predicate → 从 putMap 移除（不 进 removeKeys）
        // a 的 value 是 "1"，匹配 Predicate → 从 initMap 移除（进 removeKeys）
        Assert.assertEquals(1, map.getRemovedEntries().size()); // 仅 a
        Assert.assertEquals("1", map.getRemovedEntries().get("a"));
        Assert.assertEquals(1, map.getAllEntries().size()); // 仅 c
    }

    // ===== rowIdOf =====

    @Test
    public void rowIdOf_returnsKey() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);

        Assert.assertEquals("a", map.rowIdOf("a", "1"));
    }

    // ===== 不可变读 API =====

    @Test(expected = UnsupportedOperationException.class)
    public void getInitEntries_isImmutable() {
        TrackedMap<String, String> map = new TrackedMap<>();
        map.getInitEntries().put("x", "y");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getInsertedEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.put("b", "2");
        map.getInsertedEntries().put("x", "y");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getRemovedEntries_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.remove("a");
        map.getRemovedEntries().put("x", "y");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getRemoveKeys_isImmutable() {
        Map<String, String> init = new LinkedHashMap<>();
        init.put("a", "1");
        TrackedMap<String, String> map = new TrackedMap<>(init);
        map.getRemoveKeys().add("x");
    }
}
