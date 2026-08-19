# 注册表设计：操作注册表与规则注册表

> 本文档介绍 Pragmatic DDD 中两类注册表的最佳实践：操作注册表（`OperationRegistry`）与规则注册表（`BrokenRuleRegistry`）。它们集中声明一个聚合的全部业务操作与校验消息码，聚合根通过两个抽象方法接入。前置阅读：[聚合设计原则](./aggregate-design.md)。

## 1. 两类注册表一览

| 注册表 | 作用 | 声明内容 | 聚合根接入 |
| --- | --- | --- | --- |
| `OperationRegistry` | 集中声明领域操作，供 `recordOperation` / `hasOperation*` 使用；领域事件 `operationCode` 自动回填 | `public static final EntityOperation` | `operationRegistry()`；返回 `null` = 不启用操作体系 |
| `BrokenRuleRegistry` | 集中声明校验消息码，供 `addBrokenRule` / 规则引擎使用 | `public static final MessageCode` | `brokenRuleRegistry()`；不能为 `null` |

两类注册表都由基类构造时**反射扫描子类声明的 `static` 字段**自动注册，无需手动 `register`。

## 2. 操作注册表：`OperationRegistry`

### 2.1 职责

- 定义该聚合的全部业务操作（`CREATE` / `UPDATE` / `PAY` / `CANCEL` …），聚合根 `recordOperation(...)` 只能记录注册表内声明过的操作，避免游离操作码污染因果链。
- 领域事件的 `operationCode` 自动取自最近一次 `recordOperation`，无需手动透传。
- 基类内置 `NEW`（新建）/ `DELETE`（删除）两个操作，构造时自动注册。

### 2.2 编写规范

- **必须是 `public` 类**：基类反射扫描 `static EntityOperation` 字段并 `field.get(null)`；包级私有子类会抛 `IllegalAccessException` 且被静默吞掉，导致操作未注册。
- 操作常量用 `public static final EntityOperation` 声明。
- 提供 `public static final INSTANCE` 单例，避免每次调用重复 new。
- 约定 `code` 与常量字段名完全一致，便于追踪与对账。

```java
public class OrderOperationRegistry extends OperationRegistry {

    public static final EntityOperation CREATE = EntityOperation.of("CREATE", "创建订单");
    public static final EntityOperation PAY    = EntityOperation.of("PAY", "支付订单");
    public static final EntityOperation CANCEL = EntityOperation.of("CANCEL", "取消订单");

    private OrderOperationRegistry() {}
    public static final OrderOperationRegistry INSTANCE = new OrderOperationRegistry();
}
```

## 3. 规则注册表：`BrokenRuleRegistry`

### 3.1 职责

- 集中声明聚合的校验消息码（`MessageCode`），供 `addBrokenRule`、规则引擎 `RuleCheckResult` 使用。
- 描述支持 `String.format` 占位符（`%s` / `%d` 等），配合 `addParamBrokenRule` / `RuleCheckResult.fail(Object[])` 做参数化错误消息。

### 3.2 编写规范

- **必须是 `public` 类**：原因与操作注册表相同；未注册的消息码 `getRuleDescription` 返回空串（仅影响描述文本，不影响 code 与异常抛出）。
- 消息码常量用 `public static final MessageCode` 声明。
- 提供 `public static final INSTANCE` 单例。

```java
public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode AMOUNT_POSITIVE = MessageCode.of("AMOUNT_POSITIVE", "订单金额必须大于 0");
    public static final MessageCode ITEM_REQUIRED   = MessageCode.of("ITEM_REQUIRED", "订单至少包含一个商品");

    private OrderBrokenRuleRegistry() {}
    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();
}
```

### 3.3 校验码命名

- 不带聚合/领域前缀，全大写下划线。
- 跨聚合允许重名（各自独立注册，互不影响）。
- 不收敛为有限枚举词表——新增业务类型时直接加新码，不用改公共词表。

## 4. 与聚合根的关系

聚合根通过两个抽象方法返回注册表单例：

```java
@Override
protected BrokenRuleRegistry brokenRuleRegistry() {
    return OrderBrokenRuleRegistry.INSTANCE;
}

@Override
protected OperationRegistry operationRegistry() {
    return OrderOperationRegistry.INSTANCE;
}
```

> **关键差异**：`brokenRuleRegistry()` 不能返回 `null`；`operationRegistry()` 返回 `null` 代表不启用操作体系——此时调用 `recordOperation` / `hasOperation*` 抛 `OperationException`，但 `collectEvent` 仍可用（事件的 `operationCode` 为 `null`）。

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 注册表写成包级私有 | 反射注册失败，操作/消息码未注册 | 注册表子类必须为 `public` |
| `code` 与常量字段名不一致 | 追踪与对账困难 | 约定 `code` 与常量字段名完全一致 |
| 校验码收敛为公共枚举词表 | 新增业务需改动公共词表、跨聚合耦合 | 各自独立注册，允许重名 |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根的编码规范与父类能力
- [校验规则领域服务](./rule-validation.md)：校验规则领域服务的编排与触发
- [事件建模指南](./event-modeling.md)
- [核心：领域操作](../core/domain-operation.md)：`OperationRegistry` / `TriggeredOperations` 机制详解
- [核心：业务规则引擎](../core/business-rules.md)：`MessageCode` / 规则引擎详解
