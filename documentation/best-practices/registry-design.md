# 规则注册表设计

> 本文档介绍 Pragmatic DDD 中规则注册表（`BrokenRuleRegistry`）的设计指导原则：集中声明聚合的校验消息码，供 `addBrokenRule` / `addParamBrokenRule` 与规则引擎使用。操作注册表（`OperationRegistry`）已拆分至独立文档：[操作注册表设计](./operation-registry-design.md)。前置阅读：[聚合设计原则](./aggregate-design.md)。

## 1. 规则注册表解决什么问题

聚合根组合持有规则违反收集器（`BrokenRuleObject`），收集校验失败的消息码。规则注册表把这类消息码**集中声明为常量**，避免魔法字符串散落业务代码，并让参数化错误消息有统一的描述来源。

- 集中声明聚合的校验消息码（`MessageCode`），供 `addBrokenRule` / `addParamBrokenRule`、规则引擎 `RuleCheckResult` 使用。
- 描述支持 `String.format` 占位符（`%s` / `%d` 等），配合 `addParamBrokenRule` / `RuleCheckResult.fail(Object[])` 做参数化错误消息。
- 基类构造时**反射扫描子类声明的 `static MessageCode` 字段**自动注册，无需手动 `register`。

## 2. 基类实际行为（基于 `io.pragmatic.ddd.base`）

### 2.1 反射自动注册机制

`BrokenRuleRegistry` 构造时遍历 `getClass().getDeclaredFields()`，对**本子类声明**的 `static` 且类型为 `MessageCode` 的字段调用 `field.get(null)` 自动注册；字段访问失败（`IllegalAccessException`）被**静默忽略**，注册继续。

### 2.2 关键约束：子类必须 `public`

与操作注册表同理：反射 `field.get(null)` 在基类构造器中执行，包级私有子类会让 `IllegalAccessException` 被静默吞掉，导致消息码**未注册**——但症状比操作注册表更隐蔽：未注册码仅导致 `getRuleDescription(key)` 返回空串（影响描述文本），不影响 `code` 与异常抛出。

```java
// ✅ 推荐：public 子类
public class OrderBrokenRuleRegistry extends BrokenRuleRegistry { ... }

// ❌ 反模式：包级私有子类，消息码未注册，描述静默为空
class OrderBrokenRuleRegistry extends BrokenRuleRegistry { ... }
```

### 2.3 消息码 `MessageCode`：record、仅按 `localCode` 判等

`MessageCode` 是 Java 17 `record`（不可变值对象），作为消息表 key 与异常 `code`：

| 规则 | 说明 |
| --- | --- |
| 构造 | 仅经 `of(localCode, description)` / `of(localCode)` 工厂创建 |
| 相等性 | **仅按 `localCode` 判定**（`description` 不参与），注册表以 `code()` 为 key |
| 不可变 | `record` 组件均 `final` |

### 2.4 未注册码的兜底行为

`getRuleDescription(key)` 对未注册的 key 返回空串（`code != null ? code.description() : ""`）；`createException(key)` / `createExceptionWithParam(key, params)` 基于该描述构造 `BrokenRuleException`。因此未注册仅让描述文本为空，不抛错——这正是它比操作注册表更"隐蔽"的原因，编写时务必保证子类 `public`。

## 3. 编写规范

### 3.1 完整示例

```java
public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode AMOUNT_POSITIVE = MessageCode.of("AMOUNT_POSITIVE", "订单金额必须大于 0");
    public static final MessageCode ITEM_REQUIRED   = MessageCode.of("ITEM_REQUIRED", "订单至少包含一个商品");

    private OrderBrokenRuleRegistry() {}

    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();
}
```

### 3.2 类与常量可见性

- 注册表子类**必须为 `public`**（反射注册前提，见 §2.2）。
- 消息码常量用 `public static final MessageCode` 声明，供 `addBrokenRule(OrderBrokenRuleRegistry.AMOUNT_POSITIVE)` 引用。

### 3.3 校验码命名

- 不带聚合/领域前缀，全大写下划线。
- 跨聚合允许重名（各自独立注册，互不影响）。
- 不收敛为有限枚举词表——新增业务类型时直接加新码，不用改公共词表。

### 3.4 单例 `INSTANCE` 与私有构造

提供 `public static final INSTANCE` 单例，聚合根每次 `brokenRuleRegistry()` 返回同一个实例；构造器设为 `private`。若规则临时性强、无需独立类，可用基类静态工厂 `BrokenRuleRegistry.of(code1, code2, ...)` 内联构建。

### 3.5 参数化消息

描述支持 `String.format` 占位符，配合参数化 API 使用：

```java
public static final MessageCode AMOUNT_RANGE =
        MessageCode.of("AMOUNT_RANGE", "订单金额必须在 %s ~ %s 之间");

// 聚合根内：addParamBrokenRule(MessageCode, 参数, 是否自动格式化)
addParamBrokenRule(OrderBrokenRuleRegistry.AMOUNT_RANGE, new Object[]{min, max}, true);
```

### 3.6 与聚合根接入

```java
@Override
protected BrokenRuleRegistry brokenRuleRegistry() {
    return OrderBrokenRuleRegistry.INSTANCE;
}
```

> **关键差异**：`brokenRuleRegistry()` **不能返回 `null`**；而 `operationRegistry()` 返回 `null` 代表不启用操作体系（详见 [操作注册表设计](./operation-registry-design.md)）。

## 4. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 注册表写成包级私有 | 反射注册失败，消息码未注册、描述静默为空 | 注册表子类必须为 `public` |
| `code` 与常量字段名不一致 | 追踪与对账困难 | 约定 `code` 与常量字段名完全一致 |
| 校验码收敛为公共枚举词表 | 新增业务需改动公共词表、跨聚合耦合 | 各自独立注册，允许重名 |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根的编码规范与父类能力
- [操作注册表设计](./operation-registry-design.md)：领域操作（`OperationRegistry`）的编写规范
- [校验规则领域服务](./rule-validation.md)：校验规则领域服务的编排与触发
- [事件建模指南](./event-modeling.md)
- [核心：业务规则引擎](../core/business-rules.md)：`MessageCode` / 规则引擎详解
