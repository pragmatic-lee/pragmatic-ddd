# BP 卡：普通实体设计（entity-design）

> 源：`documentation/best-practices/entity-design.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

聚合内有独立身份的"零件"——如订单聚合的 `OrderItem`。与聚合根区别：实体**不承载业务规则、不校验、不发布事件**；规则/事件归聚合根，实体只承装数据与行为，是聚合根的"零部件"。

## 2. 判定：实体还是值对象

| 特征 | 实体（Entity） | 值对象（ValueObject） |
| --- | --- | --- |
| 独立身份 | 有（`id`） | 无（靠属性值判等） |
| 生命周期 | 随父聚合存在，有独立行 | 随父表 JSON |
| 典型 | `OrderItem` | `Money` / `Address` |
| Lombok | `@Setter` 按需（改状态） | 全 `final` + 不可变 |

对应表设计：有独立行 → 子表 + `TrackedList`（见 `aggregate-database-design.md`）；无身份值对象 → 父表 JSON 列。

## 3. 结构

- 继承 `AbstractEntity<ID>`：自动获得 `entityId` / 审计列 / `equals`+`hashCode`（基于 entityId）。
- **不要覆盖 `equals` / `hashCode`**：父类已基于 entityId 托管；覆盖会破坏标识语义。
- 状态变更方法直接改字段（可配 `@Setter`）。
- 改写实体 ID 须用 `id` 字段反射（勿直接 setter）。
- **字段与业务方法注释**：字段命中金额 / 时间 / 编码 / 派生 / 外部回填 / 非直观命名任一类时必写业务口径（如 `subtotal` 由 `price × quantity` 派生）；公有业务方法写「意图 + 影响哪些状态 + 是否 `markModified`」；同一类内风格一致。详见 `code-comment-specification.md`。

## 4. 与聚合根的协作

- 实体集合用 `TrackedList<OrderItem, Long>`，挂在聚合根字段上；聚合根业务方法改实体集合。
- 实体不发布事件；需要事件由聚合根 `collectEvent` 统一发。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 实体里写业务规则 | 规则上提到聚合根 |
| 覆盖实体 `equals`/`hashCode` | 用 `AbstractEntity` 托管 |
| 实体直接发事件 | 聚合根统一 `collectEvent` |
| 把无身份值对象当实体建子表 | 值对象并入父表 JSON |

## 6. 被读于

S4 `ddd-aggregate-create`（步骤 2 子实体）、S7 `ddd-code-review`。
