# 普通实体设计原则

> 本文档介绍 Pragmatic DDD 中普通实体（非聚合根的子实体）的设计与编码最佳实践：什么是普通实体、唯一标识、继承体系、编写规范与常见反模式。前置阅读：[聚合设计原则](./aggregate-design.md) / [值对象最佳实践](./value-object.md)。

## 1. 什么是普通实体

普通实体是**聚合内部**具有独立身份的子实体（如订单聚合中的 `OrderItem`），与聚合根一样有唯一标识，但**不构成聚合边界**——不承载规则校验、乐观锁版本、领域事件与操作追踪这些聚合级能力，也不对外暴露，只被聚合根组合持有。

三类领域对象对照：

| 类型 | 唯一标识 | 是否聚合边界 | 继承 |
| --- | --- | --- | --- |
| 聚合根 | 有 | 是 | `AggregateRoot<T>` |
| 普通实体 | 有 | 否（隶属于某个聚合） | `AbstractEntity<T>` |
| 值对象 | 无（结构判等） | — | `ValueObject` / `IValueObject` |

## 2. 唯一标识

普通实体的唯一标识由 `entityId`（`AbstractEntity` 托管字段）承载；等同性由框架基于 ID 实现——**ID 相同即同一实体，与业务字段无关**。

- **标识粒度**：聚合内唯一即可（如 `OrderItem` 在 `Order` 聚合内唯一），不需要全局唯一。
- **等同性**：`equals` / `hashCode` / `toString` 由 `AbstractEntity` 基于 `entityId` 提供，**不要覆盖**。
- **变更追踪**：实体放入 `TrackedList` / `TrackedMap` 做增量持久化时，实现 `ITrackable<T>` 提供持久化行标识 `id()`。

## 3. 继承体系

普通实体继承 `AbstractEntity<T>`，不需要像聚合根那样实现 `brokenRuleRegistry()` / `operationRegistry()` 两个抽象方法：

```java
public class OrderItem extends AbstractEntity<Long> implements ITrackable<Long> {

    private Long productId;
    private String productName;
    private int quantity;

    // 业务构造 + getter（略）

    @Override
    public Long id() {
        return this.getEntityId();
    }
}
```

`AbstractEntity<T>` 已提供：

| 成员 | 说明 |
| --- | --- |
| `entityId` | 唯一标识，经 `setEntityId(T)` 赋值 |
| `entityDelete` | 软删标记 |
| `createdAt` / `updatedAt` / `createdBy` / `updatedBy` | 审计字段 |
| `markCreated()` / `markModified()` | 审计时间戳写入 |
| `equals` / `hashCode` / `toString` | 基于 `entityId` 的等同性 |

## 4. 编写规范

### 4.1 继承 `AbstractEntity<T>`（+ `ITrackable`）

需要放入 `TrackedList` / `TrackedMap` 做变更追踪的实体实现 `ITrackable<T>`（`id()` 返回持久化行键）；整体 JSON 存储的子实体可省略。

### 4.2 构造函数

- **业务构造**：设置 `entityId`、属性赋值、按需 `markCreated()`；**不做规则校验**（校验由聚合根的规则引擎统一处理）。
- **无参 `protected` 构造**：仅供持久化框架重建，不触发业务逻辑。

```java
public OrderItem(Long id, Long productId, String productName, int quantity) {
    this.setEntityId(id);
    this.productId = productId;
    this.productName = productName;
    this.quantity = quantity;
}

protected OrderItem() {
    // 持久化重建专用，空实现
}
```

**入参过多时用 `IParamObject` 收敛**：构造参数过多（一般超过 5 个，或参数明显成组出现）时，不要逐个列参，封装成参数对象整体传入，参数对象实现 `IParamObject` 标记接口、加 `@Data` 即可——`IParamObject` 是纯数据容器，不需要手写构造函数。详见 [聚合设计原则](./aggregate-design.md) 的 §3.4。

```java
@Data
public class OrderItemInitData implements IParamObject {
    private Long id;
    private Long productId;
    private String productName;
    private int quantity;
}

public OrderItem(OrderItemInitData data) {
    this.setEntityId(data.getId());
    this.productId = data.getProductId();
    this.productName = data.getProductName();
    this.quantity = data.getQuantity();
}
```

### 4.3 业务方法

修改自身状态，按需 `markModified()`；同样不做规则校验：

```java
public void changeQuantity(int quantity) {
    this.quantity = quantity;
    this.markModified();
}
```

### 4.4 Lombok 约定

字段 `@Getter` + `@Setter(AccessLevel.PROTECTED)`，重建构造 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`；**禁用 `@Data` / `@EqualsAndHashCode` / `@Builder`**（等同性由 `AbstractEntity` 托管）。

### 4.5 归属聚合

普通实体只能被**聚合根组合持有**（如 `TrackedList<OrderItem, Long>`），不直接暴露给应用层 / 接口层；外部访问一律经聚合根。

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 普通实体覆盖 equals/hashCode | 破坏基于 ID 的等同性 | 交给 `AbstractEntity` |
| 普通实体独立暴露给外部 | 绕过聚合根破坏不变性 | 只经聚合根访问 |
| 在普通实体里做规则校验 | 校验散落、方法不可测 | 由聚合根规则引擎统一校验 |
| 用值对象表达有身份的子实体 | 丢失身份、无法独立追踪 | 用 `AbstractEntity` 实体 |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根与子实体的关系
- [值对象最佳实践](./value-object.md)：无身份的结构判等数据
- [核心：领域建模](../core/domain-modeling.md)：`IEntity` / `AbstractEntity` 机制详解
- [核心：变更追踪](../core/change-tracking.md)：`ITrackable` / `TrackedList` 增量持久化
