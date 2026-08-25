# 规则注册表设计

> 本文档介绍 Pragmatic DDD 中规则注册表（`BrokenRuleRegistry`）的落地方式：先明确它解决什么问题，再给出命名与包结构、落地代码骨架，最后是易错点与反模式。前置阅读：[聚合设计原则](./aggregate-design.md)。

## 1. 本质与定位

规则注册表把聚合的**校验消息码集中声明为常量**，供聚合根的 `addBrokenRule` / `addParamBrokenRule` 与规则容器引用，消灭散落业务代码的魔法字符串。

- 解决什么：校验失败时的 `code` + 可参数化描述文案，统一收口到一处；聚合根组合持有规则违反收集器（`BrokenRuleObject`），消息码是它收集的最小单元。
- 核心特征：`public static final MessageCode` 常量 + 单例 `INSTANCE`；基类构造时自动扫描注册，无需手动 `register`。
- 与写模型的边界：只声明消息码，**不含校验逻辑**；校验逻辑在规则容器 / 领域服务（见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)）。

## 2. 命名与包结构

注册表类放在聚合的领域层，与模型 / 规则 / 服务平级：

```text
domain/order/
├── model/         聚合根、实体、值对象
├── rule/          规则容器 + 注册表
│   ├── OrderRule.java
│   └── OrderRuleRegistry.java
└── service/       领域服务契约
```

| 命名 | 约定 | ✅ 示例 | ❌ 反例 |
| --- | --- | --- | --- |
| 注册表类 | `{聚合}RuleRegistry`，继承 `BrokenRuleRegistry` | `OrderRuleRegistry` | `OrderBrokenRules` |
| 消息码常量 | `public static final MessageCode`，全大写下划线 | `AMOUNT_POSITIVE` | `orderAmountPositive` |
| 单例 | `public static final INSTANCE` | `OrderRuleRegistry.INSTANCE` | `new OrderRuleRegistry()` |

> 命名风格与操作注册表（`OperationRegistry`）一致，见 [操作注册表设计](./operation-registry-design.md)。

## 3. 数据 / 职责承载

| 承载 | 不承载 |
| --- | --- |
| 聚合全部不变量的 `MessageCode`（localCode + 描述模板） | 校验逻辑（规则容器 / 领域服务负责） |
| 参数化描述（`String.format` 占位符 `%s` / `%d`） | 跨聚合耦合（各聚合注册表独立，允许 code 重名） |

消息码描述支持占位符，配合 `addParamBrokenRule` / `RuleCheckResult.fail(Object[])` 做参数化错误消息。

## 4. 落地方式（核心）

落地三步：声明消息码常量 → 提供单例 → 聚合根接入。

### 4.1 完整代码骨架

```java
public class OrderRuleRegistry extends BrokenRuleRegistry {

    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();

    public static final MessageCode ORDER_AMOUNT_POSITIVE =
            MessageCode.of("ORDER_AMOUNT_POSITIVE", "订单金额必须为正数");

    public static final MessageCode ORDER_AT_LEAST_ONE_ITEM =
            MessageCode.of("ORDER_AT_LEAST_ONE_ITEM", "订单至少包含一个订单项");

    private OrderRuleRegistry() {
    }
}
```

### 4.2 编写规则

- 注册表子类**必须为 `public`**，消息码常量用 `public static final MessageCode` 声明。
- `code` 与常量字段名**完全一致**，便于追踪与对账：`ORDER_AMOUNT_POSITIVE = MessageCode.of("ORDER_AMOUNT_POSITIVE", ...)`。
- **不带聚合/领域前缀**，全大写下划线；跨聚合允许同 `code` 重名（各自注册表独立，互不影响）。
- 不收敛为公共枚举词表——新增业务类型直接加新码，不改公共词表。
- 提供 `public static final INSTANCE` 单例与 `private` 构造，避免重复 new；规则临时性强时可用基类静态工厂 `BrokenRuleRegistry.of(code1, code2, ...)` 内联构建，无需独立类。

### 4.3 参数化消息

```java
public static final MessageCode AMOUNT_RANGE =
        MessageCode.of("AMOUNT_RANGE", "订单金额必须在 %s ~ %s 之间");

// 聚合根内：addParamBrokenRule(MessageCode, 参数, 是否自动格式化)
addParamBrokenRule(OrderRuleRegistry.AMOUNT_RANGE, new Object[]{min, max}, true);
```

### 4.4 聚合根接入

```java
@Override
protected BrokenRuleRegistry brokenRuleRegistry() {
    return OrderRuleRegistry.INSTANCE;
}
```

> ⚠️ **重要约束**：`brokenRuleRegistry()` **不能返回 `null`**；而 `operationRegistry()` 返回 `null` 代表不启用操作体系（见 [操作注册表设计](./operation-registry-design.md)）。

## 5. 关键机制与避坑

- **反射自动注册，子类必须 `public`**：基类构造时遍历子类声明的 `static MessageCode` 字段自动注册。子类为包级私有时反射 `field.get(null)` 失败被**静默吞掉**，消息码未注册——症状比操作注册表更隐蔽：`code` 照常抛出，但**描述文本为空**。这是最容易踩的坑。
- **`MessageCode` 仅按 `localCode` 判等**：注册表以 `code` 为 key，`description` 不参与判等。同一注册表内 `localCode` 不可重复，重复注册后者覆盖前者。
- **未注册码的兜底**：`getRuleDescription(key)` 对未注册 key 返回空串，不影响 `code` 与异常抛出——这正是未注册难以及时发现的原因。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 注册表写成包级私有 | 反射注册失败，消息码未注册、描述静默为空 | 注册表子类必须 `public` |
| `code` 与常量字段名不一致 | 追踪与对账困难 | 约定 `code` 与常量字段名完全一致 |
| 校验码收敛为公共枚举词表 | 新增业务需改动公共词表、跨聚合耦合 | 各自独立注册，允许重名 |
| 每次 `brokenRuleRegistry()` 都 new 一个注册表 | 浪费实例、语义漂移 | 提供 `public static final INSTANCE` 单例 |
| 注册表内混入校验逻辑 | 职责混杂、注册表难以复用 | 校验逻辑放规则容器 / 领域服务 |

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根编码规范与父类能力
- [操作注册表设计](./operation-registry-design.md)：领域操作（`OperationRegistry`）的编写规范
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：规则容器如何引用注册表码、外部校验契约注入
- [核心：业务规则引擎](../core/business-rules.md)：`MessageCode` / 规则引擎详解
