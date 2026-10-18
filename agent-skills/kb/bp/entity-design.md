# BP 卡：普通实体设计原则（entity-design）

> 源：`documentation/best-practices/entity-design.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 什么是普通实体

普通实体是**聚合内部**具有独立身份的子实体（如 `OrderItem`），与聚合根一样有唯一标识，但**不构成聚合边界**——不承载规则校验、乐观锁版本、领域事件与操作追踪，只被聚合根组合持有。

| 类型 | 唯一标识 | 是否聚合边界 | 继承 |
| --- | --- | --- | --- |
| 聚合根 | 有 | 是 | `AggregateRoot<T>` |
| 普通实体 | 有 | 否（隶属某聚合） | `AbstractEntity<T>` |
| 值对象 | 无（结构判等） | — | `ValueObject`/`IValueObject` |

## 2. 唯一标识

- 标识由 `entityId`（`AbstractEntity` 托管）承载；等同性由框架基于 ID 实现——**ID 相同即同一实体，与业务字段无关**。
- 标识粒度：聚合内唯一即可，不需全局唯一。
- 变更追踪：实体放入 `TrackedList`/`TrackedMap` 做增量持久化时，实现 `ITrackable<T>` 提供持久化行标识 `id()`。

## 3. 继承体系

继承 `AbstractEntity<T>`，**不需要**像聚合根那样实现 `brokenRuleRegistry()`/`operationRegistry()`：

```java
public class OrderItem extends AbstractEntity<Long> implements ITrackable<Long> {
    private Long productId;
    private String productName;
    private int quantity;

    public OrderItem(Long id, Long productId, String productName, int quantity) {
        this.setEntityId(id);
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }

    protected OrderItem() { /* 持久化重建专用 */ }

    @Override
    public Long id() { return this.getEntityId(); }
}
```

`AbstractEntity<T>` 已提供：`entityId` / `entityDelete` / 审计字段 / `markCreated()`/`markModified()` / 基于 `entityId` 的 `equals`/`hashCode`/`toString`。

## 4. 编写规范

- **继承 `AbstractEntity<T>`（+`ITrackable`）**：需放入 `TrackedList`/`TrackedMap` 做变更追踪的实体实现 `ITrackable<T>`（`id()` 返回持久化行键）；整体 JSON 存储的子实体可省略。
- **构造函数**：业务构造设置 `entityId`、属性赋值、按需 `markCreated()`，**不做规则校验**；无参 `protected` 仅供持久化重建。入参过多（>5 或成组）用 `IParamObject` 收敛（`@Data` 即可）。
- **业务方法**：修改自身状态、按需 `markModified()`，同样不做规则校验。
- **Lombok**：字段 `@Getter`+`@Setter(AccessLevel.PROTECTED)`，重建构造 `@NoArgsConstructor(PROTECTED)`；**禁用 `@Data`/`@EqualsAndHashCode`/`@Builder`**。
- **归属**：只被聚合根组合持有（如 `TrackedList<OrderItem, Long>`），不直接暴露给应用层/接口层。
- **注释**：字段命中金额/时间/编码/派生/外部回填/非直观命名任一类必须写业务口径；业务方法写「意图+影响状态+是否 `markModified`」。完整标准见 `code-comment-specification.md`。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 覆盖 equals/hashCode | 交给 `AbstractEntity` |
| 独立暴露给外部 | 只经聚合根访问 |
| 在实体里做规则校验 | 由聚合根规则引擎统一校验 |
| 用值对象表达有身份子实体 | 用 `AbstractEntity` 实体 |

## 6. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
