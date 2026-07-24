package io.pragmatic.ddd.track;

import java.util.*;
import java.util.function.Predicate;

/**
 * 键值对集合的<b>变更追踪容器</b>，把 Map 状态拆为 基线/待PUT/待删除 三桶，
 * 使持久化只做增量 INSERT/UPDATE/DELETE，而非"全删全插"。
 *
 * <h2>与 TrackedList 对称设计</h2>
 * <p>与 {@link TrackedList} 共享相同哲学：构造不遍历、不可变读 API、去回调。</p>
 * <ul>
 *   <li>Map 的 {@code key} 是内置行标识，因此不需要 {@code ITrackable} 约束；</li>
 *   <li>构造器直接赋引用，不拷贝、不遍历（MyBatis 懒加载代理原样传入）；</li>
 *   <li>读 API 全部返回不可变快照（{@code Map.copyOf} / {@code Set.copyOf}）。</li>
 * </ul>
 *
 * <h2>物理 UPDATE 一等公民</h2>
 * <p>Map 的 {@code key} 提供"原地定位"能力——{@link #put(Object, Object)} 在同一
 * {@code key} 上替换 value，语义上就是"对 key 这一行发一条 UPDATE"，无需删旧插新。</p>
 * <ul>
 *   <li>{@code put(k, v2)} 且 k 不在基线 → INSERT（{@link #getInsertedEntries()}）；</li>
 *   <li>{@code put(k, v2)} 且 k 已在基线 → UPDATE 同一行（{@link #getUpdatedEntries()}）；</li>
 *   <li>{@code remove(k)} 且 k 在基线 → DELETE（{@link #getRemovedEntries()}）。</li>
 * </ul>
 *
 * <h2>逻辑替换兜底</h2>
 * <p>若需"删旧插新"语义，可对同一 key 先 {@link #remove(Object)} 再
 * {@link #put(Object, Object)}：该 key 从 {@code getUpdatedEntries()} 移到
 * {@code getInsertedEntries()}（删了又加=净重新新增），持久化发 INSERT 新行。</p>
 *
 * @param <K> Map 的 key 类型（同时作为持久化行标识）
 * @param <V> Map 的 value 类型
 */
public class TrackedMap<K, V> {

    // ===== 三桶 =====
    private final Map<K, V> initMap;            // 基线
    private final Map<K, V> putMap = new LinkedHashMap<>();     // 待 PUT（新增 + 替换）
    private final Set<K> removeKeys = new LinkedHashSet<>();    // 待 DELETE 的 key

    // ===== 构造器 =====

    /** 空基线。 */
    public TrackedMap() {
        this.initMap = new LinkedHashMap<>();
    }

    /**
     * 带基线构造器。
     * <p>直接赋引用，不遍历——MyBatis 懒加载代理原样传入，不会触发子查询。</p>
     *
     * @param init 从 DB 加载的基线集合
     */
    public TrackedMap(Map<K, V> init) {
        this.initMap = init;
    }

    // ===== 变更 API =====

    /**
     * 放入/替换一个 entry。
     * <ul>
     *   <li>key 已在基线 → UPDATE 候选（{@link #getUpdatedEntries()}）；</li>
     *   <li>key 不在基线 → INSERT 候选（{@link #getInsertedEntries()}）。</li>
     * </ul>
     */
    public void put(K key, V value) {
        this.putMap.put(key, value);
    }

    /** 批量放入。 */
    public void putAll(Map<K, V> entries) {
        entries.forEach(this::put);
    }

    /**
     * 按 key 移除。
     * <ul>
     *   <li>key 在 initMap 中 → 进 removeKeys（发 DELETE）；</li>
     *   <li>key 仅刚 PUT 未持久化 → 直接从 putMap 移除，不进 removeKeys。</li>
     * </ul>
     */
    public void remove(K key) {
        if (this.initMap.containsKey(key)) {
            this.removeKeys.add(key);
        }
        this.putMap.remove(key);
    }

    /** 按 value 条件批量移除（遍历基线 + putMap）。 */
    public void removeEntry(Predicate<? super V> predicate) {
        this.initMap.forEach((k, v) -> {
            if (predicate.test(v)) this.removeKeys.add(k);
        });
        this.putMap.entrySet().removeIf(e -> predicate.test(e.getValue()));
    }

    /** 清空：所有基线 key 进 removeKeys，putMap 清空。 */
    public void clear() {
        this.removeKeys.addAll(this.initMap.keySet());
        this.putMap.clear();
    }

    // ===== 读 API（全部不可变快照） =====

    /** 基线（DB 加载时已有的 entry）。 */
    public Map<K, V> getInitEntries() {
        return Map.copyOf(this.initMap);
    }

    /** 待 PUT 的 entry（含新增 + 替换）。 */
    public Map<K, V> getPutEntries() {
        return Map.copyOf(this.putMap);
    }

    /** 待 DELETE 的 key 集合。 */
    public Set<K> getRemoveKeys() {
        return Set.copyOf(this.removeKeys);
    }

    /**
     * 待 INSERT 的 entry：putMap 中 key 不在基线、或基线中但曾被 remove 标记
     * （删了又加=重新新增）。
     */
    public Map<K, V> getInsertedEntries() {
        Map<K, V> r = new LinkedHashMap<>();
        this.putMap.forEach((k, v) -> {
            if (!this.initMap.containsKey(k) || this.removeKeys.contains(k)) r.put(k, v);
        });
        return Map.copyOf(r);
    }

    /**
     * 待 UPDATE 的 entry：putMap 中 key 在基线且未被 remove 标记
     * （同一 key，物理 UPDATE）。
     */
    public Map<K, V> getUpdatedEntries() {
        Map<K, V> r = new LinkedHashMap<>();
        this.putMap.forEach((k, v) -> {
            if (this.initMap.containsKey(k) && !this.removeKeys.contains(k)) r.put(k, v);
        });
        return Map.copyOf(r);
    }

    /**
     * 待 DELETE 的 key（及其原值）。
     * 排除"也被重新 PUT"的 key（净重新新增，不在此发 DELETE）。
     */
    public Map<K, V> getRemovedEntries() {
        Map<K, V> r = new LinkedHashMap<>();
        this.removeKeys.forEach(k -> {
            if (!this.putMap.containsKey(k)) r.put(k, this.initMap.get(k));
        });
        return Map.copyOf(r);
    }

    /** 当前逻辑视图：基线（未删除）+ putMap 的并集。 */
    public Map<K, V> getAllEntries() {
        Map<K, V> all = new LinkedHashMap<>(this.initMap);
        this.removeKeys.forEach(all::remove);
        all.putAll(this.putMap);
        return Map.copyOf(all);
    }

    /** 基线中本次未被删除、也未被替换的 entry 全集（完全未变项）。 */
    public Map<K, V> getRetainedEntries() {
        Map<K, V> r = new LinkedHashMap<>(this.initMap);
        this.removeKeys.forEach(r::remove);
        this.putMap.keySet().forEach(r::remove);
        return Map.copyOf(r);
    }

    /**
     * 持久化行标识：key 即行标识。
     * <p>与 TrackedList 的 {@code T.id()} 语义对称。</p>
     */
    public K rowIdOf(K key, V value) {
        return key;
    }

    // ===== equals / hashCode =====

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackedMap<?, ?> that)) return false;
        return Objects.equals(initMap, that.initMap)
                && Objects.equals(putMap, that.putMap)
                && Objects.equals(removeKeys, that.removeKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(initMap, putMap, removeKeys);
    }
}
