package io.pragmatic.ddd.base;

/**
 * 聚合根基类。
 *
 * <p>聚合根是 DDD 聚合的唯一外部入口点，负责维护聚合内部不变性约束。
 * 所有需要通过仓储持久化的实体都必须继承此类。</p>
 *
 * <p>与 EntityBase 的关系：</p>
 * <ul>
 *   <li>聚合内部的值对象 → 纯 POJO，不继承任何框架类（大多数情况）</li>
 *   <li>极少数需要独立 ID 的内部实体 → 继承 EntityBase（有身份、可追踪）</li>
 *   <li>聚合根 → 继承 AggregateRoot（有完整仓储能力，编译期约束入口）</li>
 * </ul>
 *
 * <p>标准用法：</p>
 * <pre>{@code
 * // 聚合根
 * public class Order extends AggregateRoot<Long> {
 *     private List<OrderLine> lines;      // ← 值对象，纯 POJO
 *     private Address shippingAddress;    // ← 值对象，纯 POJO
 * }
 *
 * // 值对象——无框架依赖
 * public class OrderLine {
 *     private String productId;
 *     private int quantity;
 *     private Money unitPrice;
 * }
 *
 * // 极少数需要身份的内部实体
 * public class ApprovalHistory extends EntityBase<Long> {
 *     // 有 ID、有版本号，但无独立 Repository（编译期保证）
 * }
 * }</pre>
 *
 * @param <T> 标识类型
 * @author Li XiaoJing
 * @since 2.1.0
 */
public abstract class AggregateRoot<T> extends AbstractEntity<T> {
    // 纯类型标记，不添加额外 API
}
