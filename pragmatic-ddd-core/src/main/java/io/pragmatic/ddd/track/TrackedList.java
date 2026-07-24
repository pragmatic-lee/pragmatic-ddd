package io.pragmatic.ddd.track;

import java.util.*;
import java.util.function.Predicate;

/**
 * 一对多集合的<b>变更追踪容器</b>，把集合状态拆为 基线/新增/删除 三桶，
 * 使持久化只做增量 INSERT/DELETE，而非"全删全插"。
 *
 * <h2>行标识契约：{@code T implements ITrackable<ID>}</h2>
 * <p>所有元素必须有唯一的持久化行标识，通过 {@link ITrackable#id()} 获取。
 * 实体返回其 ID，独立表值对象返回其行键（业务键）。
 * 定位基线项时优先按 {@code id()} 精确匹配（走惰性 {@code initMap}），
 * 物理移除时退用 {@code equals()}（需确保 {@code T.equals()} 按 ID 等同）。</p>
 *
 * <h2>惰性 initMap</h2>
 * <p>构造器将基线列表直接赋引用（不遍历、不拷贝），所以传入 MyBatis 懒加载代理时
 * 不会触发子查询。首次调用 {@link #update(T, T)} 或
 * {@link #removeItems(Predicate)} 时才遍历基线建索引（此时也确实需要数据）。</p>
 *
 * <h2>适用对象</h2>
 * <p>本容器只针对<b>有独立 DB 行的对象类型</b>（实体或独立表值对象），
 * 不处理基础类型（{@code String}/{@code Integer}）或无身份的内嵌值对象。
 * 内嵌值对象的集合整体随父表替换，无需本容器。</p>
 *
 * <h3>结构差量 vs 字段修改</h3>
 * <p>本容器只负责集合的【结构差量】：哪些子行要 INSERT（append）、哪些要 DELETE（remove）。<br>
 * "更新集合中的某项"= 用新对象替换旧对象 = remove(旧) + append(新)：
 * 旧项移入 remove 桶（发 DELETE），新项进入 append 桶（发 INSERT）。<br>
 * 本容器【不引入】"原地 UPDATE 字段"的第四态；若业务必须"对同一行发 UPDATE 语句"
 * （如子项被外键引用），才启用备选的快照对比方案。<br>
 * 子项字段修改的"源头"由调用方负责：外部产生新对象，调用 {@link #update(T, T)}。</p>
 *
 * @param <T>  元素类型，必须实现 {@link ITrackable}
 * @param <ID> 行标识类型
 */
public class TrackedList<T extends ITrackable<ID>, ID> {

    // ===== 三桶 =====
    private List<T> initCollection; // 基线（非 final，供 MyBatis 反射设置）
    private final List<T> appendList = new ArrayList<>(); // 待 INSERT
    private final List<T> removeList = new ArrayList<>(); // 待 DELETE

    // ===== 惰性行标识索引 =====
    private Map<ID, T> initMap;

    // ===== 构造器 =====

    /** 空基线。 */
    public TrackedList() {
        this.initCollection = new ArrayList<>();
    }

    /**
     * 带基线构造器。
     * <p>直接赋引用，不遍历——MyBatis 懒加载代理原样传入，不会触发子查询。</p>
     *
     * @param init 从 DB 加载的基线集合
     */
    public TrackedList(List<T> init) {
        this.initCollection = init;
    }

    // ===== 惰性索引 =====

    private Map<ID, T> initMap() {
        if (initMap == null) {
            initMap = new LinkedHashMap<>();
            for (T item : initCollection) {
                initMap.put(item.id(), item);
            }
        }
        return initMap;
    }

    // ===== 变更 API =====

    /** 新增一个子项（发 INSERT）。 */
    public void append(T item) {
        this.appendList.add(item);
    }

    /** 批量新增。 */
    public void append(List<T> items) {
        this.appendList.addAll(items);
    }

    /**
     * 逻辑更新：用 {@code newItem} 替换 {@code oldItem}。
     * <p>内部等价于 remove(oldItem) + append(newItem)：</p>
     * <ul>
     *   <li>{@code oldItem} 从 init 移入 removeList（持久化发 DELETE）；</li>
     *   <li>{@code newItem} 进入 appendList（持久化发 INSERT）。</li>
     * </ul>
     * <p>定位方式：通过 {@code oldItem.id()} 在惰性 initMap 中按行标识查找。</p>
     *
     * @throws IllegalArgumentException 当行标识未命中基线时
     */
    public void update(T oldItem, T newItem) {
        T removed = initMap().remove(oldItem.id());
        if (removed == null) {
            throw new IllegalArgumentException(
                    "oldItem.id()=[" + oldItem.id() + "] not found in init collection");
        }
        this.initCollection.remove(removed);
        this.removeList.add(removed);
        this.appendList.add(newItem);
    }

    /**
     * 按条件移除子项（已追加的 append 项和基线项都会匹配）。
     *
     * @return 所有被移除的项（含来自 append 和 init 的）
     */
    public List<T> removeItems(Predicate<? super T> predicate) {
        // 1. 从 appendList 中移除符合条件者
        List<T> fromAppend = this.appendList.stream().filter(predicate).toList();
        this.appendList.removeAll(fromAppend);

        // 2. 从 init 中移除符合条件者（走惰性 initMap 精确迭代）
        List<T> fromInit = new ArrayList<>();
        Iterator<Map.Entry<ID, T>> it = initMap().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ID, T> entry = it.next();
            if (predicate.test(entry.getValue())) {
                fromInit.add(entry.getValue());
                it.remove();
            }
        }
        this.initCollection.removeAll(fromInit);
        this.removeList.addAll(fromInit);

        List<T> allRemoved = new ArrayList<>(fromAppend.size() + fromInit.size());
        allRemoved.addAll(fromAppend);
        allRemoved.addAll(fromInit);
        return allRemoved;
    }

    /** 清理所有子项（基线全移入 remove，清空 append）。 */
    public void removeAll() {
        this.removeList.addAll(this.initCollection);
        this.initCollection.clear();
        this.appendList.clear();
        if (this.initMap != null) {
            this.initMap.clear();
        }
    }

    /**
     * 全量替换：当前所有基线移入 remove，清空 append，{@code items} 进入 append。
     * <p>等价于 {@code removeAll()} + {@code append(items)}。</p>
     */
    public void clearAndAppend(List<T> items) {
        this.removeList.addAll(this.initCollection);
        this.initCollection.clear();
        this.appendList.clear();
        if (this.initMap != null) {
            this.initMap.clear();
        }
        this.appendList.addAll(items);
    }

    // ===== 读 API =====

    /** 待 INSERT 的子项。 */
    public List<T> getAppendedItems() {
        return List.copyOf(this.appendList);
    }

    /** 待 DELETE 的子项。 */
    public List<T> getRemovedItems() {
        return List.copyOf(this.removeList);
    }

    /**
     * 当前逻辑视图：init + append 的并集（不含已移除的项）。
     * <p>返回顺序为"基线顺序（按加入顺序）后接新增顺序"。</p>
     */
    public List<T> getAllItems() {
        List<T> all = new ArrayList<>(this.initCollection.size() + this.appendList.size());
        all.addAll(this.initCollection);
        all.addAll(this.appendList);
        return List.copyOf(all);
    }

    /** 基线（DB 加载时已有的子项）。 */
    public List<T> getInitItems() {
        return List.copyOf(this.initCollection);
    }
}
