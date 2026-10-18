# BP 卡：规则注册表设计（registry-design）

> 源：`documentation/best-practices/registry-design.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

规则注册表把聚合的**校验消息码集中声明为常量**，供聚合根 `addBrokenRule`/`addParamBrokenRule` 与规则容器引用，消灭散落业务代码的魔法字符串。

- 解决：校验失败时的 `code` + 可参数化描述文案统一收口；消息码是聚合根 `BrokenRuleObject` 收集的最小单元。
- 核心特征：`public static final MessageCode` 常量 + 单例 `INSTANCE`；基类构造时自动扫描注册，无需手动 `register`。
- 与写模型边界：只声明消息码，**不含校验逻辑**；校验逻辑在规则容器/领域服务（见 `order-rule-pattern.md`）。

## 2. 命名与包结构

```text
domain/order/
├── model/         聚合根、实体、值对象
├── rule/          规则容器 + 注册表
│   ├── OrderRule.java
│   └── OrderRuleRegistry.java
└── service/       领域服务契约
```

| 命名 | 约定 |
| --- | --- |
| 注册表类 | `{聚合}RuleRegistry`（继承 `BrokenRuleRegistry`），如 `OrderRuleRegistry` |
| 消息码常量 | `public static final MessageCode`，全大写下划线，如 `AMOUNT_POSITIVE` |
| 单例 | `public static final INSTANCE`，如 `OrderRuleRegistry.INSTANCE` |

## 3. 落地方式（核心）

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

编写规则：

- 注册表子类**必须为 `public`**，消息码常量用 `public static final MessageCode` 声明。
- `code` 与常量字段名**完全一致**；**不带聚合/领域前缀**，全大写下划线；跨聚合允许同 `code` 重名（各自注册表独立互不影响）。
- 不收敛为公共枚举词表——新增业务类型直接加新码。
- 提供 `INSTANCE` 单例与 `private` 构造；规则临时性强时可用基类静态工厂 `BrokenRuleRegistry.of(code1, code2, ...)` 内联构建，无需独立类。

### 3.1 参数化消息

```java
public static final MessageCode AMOUNT_RANGE =
        MessageCode.of("AMOUNT_RANGE", "订单金额必须在 %s ~ %s 之间");
// 聚合根内：addParamBrokenRule(MessageCode, 参数, 是否自动格式化)
addParamBrokenRule(OrderRuleRegistry.AMOUNT_RANGE, new Object[]{min, max}, true);
```

### 3.2 聚合根接入

```java
@Override
protected BrokenRuleRegistry brokenRuleRegistry() {
    return OrderRuleRegistry.INSTANCE;
}
```

> ⚠️ `brokenRuleRegistry()` **不能返回 `null`**；而 `operationRegistry()` 返回 `null` 代表不启用操作体系（见 `operation-registry-design.md`）。

## 4. 关键机制与避坑

- **反射自动注册，子类必须 `public`**：子类包级私有时反射 `field.get(null)` 失败被**静默吞掉**，消息码未注册——症状比操作注册表更隐蔽：`code` 照常抛出，但**描述文本为空**。最容易踩的坑。
- **`MessageCode` 仅按 `localCode` 判等**：`description` 不参与判等；同一注册表内 `localCode` 不可重复，重复注册后者覆盖前者。
- **未注册码兜底**：`getRuleDescription(key)` 对未注册 key 返回空串，不影响 `code` 与异常抛出——这正是未注册难以及时发现的原因。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 注册表写成包级私有 | 子类必须 `public` |
| `code` 与常量字段名不一致 | `code` 与字段名完全一致 |
| 校验码收敛为公共枚举词表 | 各自独立注册，允许重名 |
| 每次 `brokenRuleRegistry()` 都 new | 提供 `INSTANCE` 单例 |
| 注册表内混入校验逻辑 | 校验逻辑放规则容器/领域服务 |

## 6. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
