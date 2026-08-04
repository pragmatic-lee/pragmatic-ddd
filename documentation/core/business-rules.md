# 业务规则引擎

> 本文档属于 pragmatic-ddd 使用文档 `core` 系列，介绍业务规则引擎（`io.pragmatic.ddd.rules`）的核心概念与用法。
> 前置阅读：[领域建模](./domain-modeling.md)。本系列后续文档：[领域事件](./domain-events.md) · [应用服务](./application-service.md)。

## 大纲

- [1. 概述](#1-概述)
  - [1.1 规则引擎解决什么问题](#11-规则引擎解决什么问题)
  - [1.2 核心概念与协作关系](#12-核心概念与协作关系)
- [2. 校验项级契约 `ICheckRule<T>`](#2-校验项级契约-icheckrulet)
  - [2.1 新旧模型双参数](#21-新旧模型双参数)
  - [2.2 校验结果 `RuleCheckResult`](#22-校验结果-rulecheckresult)
- [3. 方式一：继承 `EntityRule<T>`（规则列表容器）](#3-方式一继承-entityrulet规则列表容器)
  - [3.1 定义与注册规则](#31-定义与注册规则)
  - [3.2 新旧对比规则](#32-新旧对比规则)
  - [3.3 failFast 模式](#33-failfast-模式)
  - [3.4 运行时动态调整规则](#34-运行时动态调整规则)
  - [3.5 规则查询](#35-规则查询)
- [4. 方式二：继承 `BaseRuleValidator<T>`（单规则快速定义）](#4-方式二继承-baserulevalidatort单规则快速定义)
- [5. 方式三：`ICheckRuleBuilder`（构造器适配）](#5-方式三icheckrulebuilder构造器适配)
- [6. 激活条件：`IActiveRuleCondition<T>`](#6-激活条件iactiveruleconditiont)
  - [6.1 模型级条件](#61-模型级条件)
  - [6.2 code 级开关](#62-code-级开关)
  - [6.3 便捷工厂与默认条件](#63-便捷工厂与默认条件)
- [7. 规则位置与消息码操作](#7-规则位置与消息码操作)
- [8. `@BusinessRule` 注解（仅标记，不参与运行期校验）](#8-businessrule-注解仅标记不参与运行期校验)
- [9. 与聚合根 `satisfiesRule` 的协作](#9-与聚合根-satisfiesrule-的协作)
- [10. 三种定义方式选型](#10-三种定义方式选型)

## 1. 概述

### 1.1 规则引擎解决什么问题

业务规则引擎用于把聚合根上的**业务不变量（invariant）**从业务方法中抽离出来，集中、可组合、可动态调整地表达"在什么条件下，一个模型是否满足约束"。

典型场景：

- 订单金额必须大于 0，且不得超过账户余额；
- 库存扣减时不能出现负库存；
- 状态流转只允许"已创建 → 已支付 → 已发货"等合法路径。

规则引擎统一约定三条原则：

1. **规则是无状态纯函数**：校验项接收「新模型」与「旧模型」双参数，不持有 per-call 可变状态，因此规则实例可作为单例安全共享。
2. **违规信息统一收集**：校验失败时写入聚合根的 `BrokenRuleObject`，最终由应用层决定抛出方式（单条或聚合异常）。
3. **运行期可动态调整**：规则支持运行时 `addRule` / `replaceRule` / `removeRule`，配合激活条件可实现配置驱动的规则开关。

### 1.2 核心概念与协作关系

```
IRule<T>                        规则的根契约（satisfiesRule）
   └── EntityRule<T>            规则列表容器（继承并覆写 init()）
         ├── RuleItem<T>        rule + messageCode + condition 的封装单元
         ├── ICheckRule<T>      校验项级契约（check(new, old) → RuleCheckResult）
         └── IActiveRuleCondition<T>  激活条件（模型级 status + code 级 switchStatus）

BaseRuleValidator<T>            单规则校验器（validate(new, old) → boolean）
ICheckRuleBuilder<T>            校验项构造器（rule() + ruleCondition()）

@BusinessRule                   方法注解，仅标记，不参与运行期校验
```

核心接口一览：

| 类型 | 名称 | 用户的关注点 |
| --- | --- | --- |
| 抽象类 | `EntityRule<T>` | 规则列表容器，实现 `IRule<T>`，用户继承并覆写 `init()` |
| 接口 | `ICheckRule<T>` | 校验项级契约，`check(T new, T old)` 返回 `RuleCheckResult` |
| 抽象类 | `BaseRuleValidator<T>` | 单规则校验器基类，实现 `validate(T, T)` 返回 boolean |
| 接口 | `ICheckRuleBuilder<T>` | 校验项构造器，将 `validate` 适配为 `ICheckRule` + 激活条件 |
| 类 | `RuleItem<T>` | 规则项封装（rule + messageCode + condition） |
| 枚举 | `RulePosition` | `LAST` / `BEFORE` / `AFTER`，控制插入位置 |
| 枚举 | `ActiveStatus` | `ACTIVE` / `INACTIVE`，控制规则是否参与校验 |
| 接口 | `IActiveRuleCondition<T>` | 激活条件，`status` 模型级 + `switchStatus` code 级 |
| 类 | `AlwaysActiveRuleCondition<T>` | 始终激活的默认条件 |
| 注解 | `@BusinessRule` | 标记方法为业务规则，AI 编码辅助 + 可视化消费 |

## 2. 校验项级契约 `ICheckRule<T>`

`ICheckRule<T>` 是对模型执行单条不变量的校验契约，是规则引擎的最小单元：

```java
@FunctionalInterface
public interface ICheckRule<T> {
    RuleCheckResult check(T newModel, T oldModel);

    default RuleCheckResult check(T newModel) {
        return check(newModel, null);
    }
}
```

### 2.1 新旧模型双参数

校验接收「新模型」与「旧模型」两个入参，使规则成为无状态纯函数：

- `newModel`：当前被校验的模型。
- `oldModel`：修改前的模型快照，创建操作或规则不需要时为 `null`。

需要**新旧对比**的规则通过 `oldModel` 获取修改前快照；不需要的规则忽略第二参数即可（用单参数便捷入口 `check(newModel)`）。

### 2.2 校验结果 `RuleCheckResult`

`RuleCheckResult` 是 `ICheckRule` 的返回值，携带校验通过/失败状态及用于消息格式化的动态参数。**创建方式全部通过静态工厂方法**，消除 `boolean` 构造函数歧义：

| 工厂方法 | 语义 |
| --- | --- |
| `pass()` | 校验通过 |
| `fail()` | 校验失败，无动态参数 |
| `fail(Object[] params)` | 校验失败，携带参数（`String.format` 格式化违规消息） |
| `fail(Object[] params, boolean enableFormat)` | 校验失败，可控制是否自动格式化 |
| `of(boolean)` | 由 boolean 直接构造 |

```java
ICheckRule<Order> amountMustBePositive = (order, old) ->
        order.getAmount() > 0 ? RuleCheckResult.pass()
                              : RuleCheckResult.fail(new Object[]{order.getAmount()});
```

失败且带参数时，`EntityRule` 会自动调用聚合根的 `addParamBrokenRule`，用 `%s` 占位符格式化为可读消息。

## 3. 方式一：继承 `EntityRule<T>`（规则列表容器）

`EntityRule<T>` 是**规则列表容器**，实现 `IRule<T>`。它是一个一维的规则列表，每条校验项通过 `ICheckRule.check` 对模型校验，违规信息统一收集。适用于**多条规则组合**的场景。

### 3.1 定义与注册规则

继承 `EntityRule<T>` 并在构造器中（或覆写 `init()`）调用 `addRule` 注册规则：

```java
public class OrderRule extends EntityRule<Order> {

    public OrderRule() {
        super(true); // failFast=true
        init();
    }

    @Override
    protected void init() {
        addRule((order, old) ->
                order.getAmount() > 0 ? RuleCheckResult.pass()
                                      : RuleCheckResult.fail(new Object[]{order.getAmount()}),
                OrderRuleRegistry.AMOUNT_POSITIVE);

        addRule(order -> order.getStatus() != null ? RuleCheckResult.pass()
                                                   : RuleCheckResult.fail(),
                OrderRuleRegistry.STATUS_NOT_NULL);
    }
}
```

`addRule` 的重载形式：

| 重载 | 说明 |
| --- | --- |
| `addRule(ICheckRule, MessageCode)` | 追加校验项，使用默认激活条件 |
| `addRule(ICheckRule, MessageCode, IActiveRuleCondition)` | 追加并指定激活条件 |
| `addRule(BaseRuleValidator, MessageCode)` | 追加校验器规则（取其内部激活条件） |
| `addRule(ICheckRuleBuilder, MessageCode)` | 追加构造器规则（取其内部激活条件） |

### 3.2 新旧对比规则

需要新旧对比的规则，覆写 `requireOldEntity()`（返回 `true`）与 `supplyOldEntity()`：

```java
public class OrderRule extends EntityRule<Order> {

    @Override
    protected boolean requireOldEntity() {
        return true; // 存在新旧对比规则，触发旧实体加载
    }

    @Override
    protected Order supplyOldEntity(Order currentModel) {
        return orderRepository.findById(currentModel.getEntityId()); // 加载修改前快照
    }

    @Override
    protected void init() {
        // 状态只能合法流转：使用新旧双参数
        addRule((order, old) -> {
            if (old == null) {
                return RuleCheckResult.pass();
            }
            boolean legal = isLegalTransition(old.getStatus(), order.getStatus());
            return legal ? RuleCheckResult.pass() : RuleCheckResult.fail();
        }, OrderRuleRegistry.ILLEGAL_STATUS_TRANSITION);
    }
}
```

- 默认 `requireOldEntity()` 返回 `false`，即**不触发任何旧实体查询**，避免无谓的 DB 访问。
- 仅当返回 `true` 时，每次 `satisfiesRule` 调用一次 `supplyOldEntity`。

### 3.3 failFast 模式

`EntityRule` 支持两种校验模式：

- **`failFast=true`（默认）**：遇第一条失败即停止校验，立即返回 `false`。适合"先判断最关键约束"的场景，性能更好。
- **`failFast=false`**：全量校验所有规则，收集所有违反。适合需要一次性告知用户所有错误的场景（如表单提交）。

```java
EntityRule<Order> failFastRule = new OrderRule(true);    // 遇首条失败即停
EntityRule<Order> collectAllRule = new OrderRule(false); // 全量收集违反
```

> `failFast=false` 时，多条违反会通过聚合根的 `throwBrokenRuleAggregateException()` 聚合成 `BrokenRuleAggregateException`（含全部子异常）。

### 3.4 运行时动态调整规则

`EntityRule` 支持运行时的增删改，配合激活条件可实现配置驱动：

```java
// 追加到列表末尾
orderRule.addRule(myCheckRule, RuleCode.MY_RULE);

// 在参照规则之后插入
orderRule.appendRule(myCheckRule, RuleCode.MY_RULE, RuleCode.REFERENCE, RulePosition.AFTER);

// 替换某条规则的消息码
orderRule.replaceRule(myCheckRule, RuleCode.OLD, RuleCode.NEW);

// 按消息码移除
orderRule.removeRule(RuleCode.OBsolete);

// 清空并重新初始化
orderRule.reset();
```

### 3.5 规则查询

```java
List<RuleItem<Order>> items = orderRule.allRuleItems();          // 全部规则项副本
ICheckRule<Order> rule = orderRule.findRuleByMessageCode(code);  // 按码查找单条（未命中返回 null）
List<ICheckRule<Order>> rules = orderRule.findRulesByMessageCode(c1, c2); // 批量查找
```

## 4. 方式二：继承 `BaseRuleValidator<T>`（单规则快速定义）

`BaseRuleValidator<T>` 将 `validate(T, T)` 适配为校验项级 `ICheckRule` 与激活条件。适用于**单条规则快速定义**：

```java
public class AmountPositiveValidator extends BaseRuleValidator<Order> {

    @Override
    protected boolean validate(Order order, Order oldOrder) {
        return order.getAmount() > 0;
    }
}
```

它把 `validate` 自动包装为 `ICheckRule`（`rule()` 方法），并默认提供始终生效的激活条件（`ruleCondition()`）。然后可将其加入 `EntityRule`：

```java
orderRule.addRule(new AmountPositiveValidator(), OrderRuleRegistry.AMOUNT_POSITIVE);
```

> **选型提示**：`BaseRuleValidator` 只关心 `boolean` 结果，无法携带动态消息参数。若需要参数化违规消息，请直接使用返回 `RuleCheckResult` 的 `ICheckRule`。

## 5. 方式三：`ICheckRuleBuilder<T>`（构造器适配）

`ICheckRuleBuilder<T>` 是一个校验项构造器，将 `validate` 适配为 `ICheckRule` 与激活条件：

```java
public interface ICheckRuleBuilder<T> {
    ICheckRule<T> rule();
    default IActiveRuleCondition<T> ruleCondition() { return null; }
}
```

通常以匿名类或 lambda 使用，把"规则逻辑"与"激活条件"打包为一个单元，便于复用和组合。

## 6. 激活条件：`IActiveRuleCondition<T>`

激活条件决定一条规则在特定模型上下文中**是否参与校验**。`IActiveRuleCondition<T>` 是函数式接口，提供两个维度的判断：

### 6.1 模型级条件

`status(T newModel, T oldModel)` 基于**模型内容**（或新旧对比）判断，返回 `ActiveStatus`：

```java
// 仅当订单状态为草稿时才校验金额规则
IActiveRuleCondition<Order> draftOnly = (order, old) ->
        order.getStatus() == OrderStatus.DRAFT ? ActiveStatus.ACTIVE
                                               : ActiveStatus.INACTIVE;

orderRule.addRule(amountCheck, RuleCode.AMOUNT, draftOnly);
```

### 6.2 code 级开关

`switchStatus(MessageCode messageCode)` 基于**规则标识**（而非模型内容）判断，常被用于读取外部动态配置（配置中心、开关平台）临时启用/停用某条规则：

```java
public class ConfigDrivenCondition<T> implements IActiveRuleCondition<T> {

    @Override
    public ActiveStatus status(T newModel, T oldModel) {
        return ActiveStatus.ACTIVE;
    }

    @Override
    public ActiveStatus switchStatus(MessageCode messageCode) {
        // 读取开关平台：若该规则码被关闭则停用
        return featureToggle.isEnabled(messageCode.code()) ? ActiveStatus.ACTIVE
                                                           : ActiveStatus.INACTIVE;
    }
}
```

**两重判定顺序**（在 `EntityRule.satisfiesRule` 中）：

1. 第一重：`switchStatus(messageCode)` —— code 级开关，决定规则整体是否启用；
2. 第二重：`status(model, oldModel)` —— 模型级条件，决定当前上下文是否参与校验。

### 6.3 便捷工厂与默认条件

`IActiveRuleCondition` 提供便捷静态工厂，以及默认条件实现：

```java
// 单参数条件
IActiveRuleCondition<Order> c1 = IActiveRuleCondition.of(
        order -> order.getStatus() == Status.DRAFT ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);

// 双参数（新旧对比）条件
IActiveRuleCondition<Order> c2 = IActiveRuleCondition.of(
        (order, old) -> old != null ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);

// 始终激活的默认条件
IActiveRuleCondition<Order> always = new AlwaysActiveRuleCondition<>();
```

> 默认 `switchStatus` 返回 `ACTIVE`（规则默认启用），既有实现无需覆盖即可获得与原先一致的行为。

## 7. 规则位置与消息码操作

`RulePosition` 枚举取代了原有的 int 魔术数字，使插入位置可直读：

| 值 | 语义 |
| --- | --- |
| `LAST` | 追加到规则列表末尾（不依赖参照规则） |
| `BEFORE` | 插入到参照规则之前 |
| `AFTER` | 插入到参照规则之后 |

配合 `appendRule` 使用：

```java
orderRule.appendRule(newRule, RuleCode.NEW, RuleCode.EXISTING, RulePosition.BEFORE);
```

## 8. `@BusinessRule` 注解（仅标记，不参与运行期校验）

`@BusinessRule` 是方法级注解，承载规则元信息，供 **AI 编码辅助**与**模型可视化系统**消费，用于记录每条规则的意图、错误码与消息：

```java
@BusinessRule(
        description = "订单金额必须大于0",
        errorCode = "ORDER_AMOUNT_POSITIVE",
        errorMessage = "订单金额必须大于0"
)
public void validateAmount() {
    // 规则校验逻辑
}
```

三个属性：`description`（规则可读描述）、`errorCode`（对应 `BrokenRuleRegistry` 中的键）、`errorMessage`（违反时展示的可读消息）。

> ⚠️ **重要**：`@BusinessRule` **仅作标记**，不参与运行期校验。它被可视化解析器反射消费，产出规则图谱（见 [模型可视化](./model-visualization.md)）。真正的运行期校验必须依赖上述 `ICheckRule` / `EntityRule` 机制。

## 9. 与聚合根 `satisfiesRule` 的协作

聚合根（`AggregateRoot<T>`）通过 `satisfiesRule(IRule)` 委托执行规则校验，并复用 `IRule` 契约：

```java
public boolean satisfiesRule(IRule<?> rule) {
    return rule != null && ((IRule) rule).satisfiesRule(this);
}
```

典型用法——在应用服务或聚合根业务方法中校验：

```java
boolean valid = order.satisfiesRule(orderRule);
if (!valid) {
    // 已收集违反，可选择抛出
    order.throwBrokenRuleException();            // 抛单条异常（取第一条）
    // 或 order.throwBrokenRuleAggregateException(); // 抛聚合异常
}
// 或直接读取违反列表做进一步处理
List<BrokenRule> brokenRules = order.getBrokenRules();
```

校验失败时，`EntityRule` 会自动把违规写入聚合根的 `BrokenRuleObject`（通过 `addBrokenRule` / `addParamBrokenRule`），应用层只需统一决定抛出策略。

> 在 [应用服务](./application-service.md) 中，`CommandExecutor` / `AbstractApplicationService` 会内建"领域逻辑 → 规则校验 → 落库 → 发布事件"的固定模板，你通常不需要手动调用 `throwBrokenRuleException`。

## 10. 三种定义方式选型

| 方式 | 适用场景 | 动态参数消息 | 组合多条 | 激活条件 |
| --- | --- | --- | --- | --- |
| `EntityRule<T>` | 多条规则组合、增删改、failFast、配置开关 | ✅ | ✅ | ✅ |
| `BaseRuleValidator<T>` | 单条快速定义，只关心 boolean 结果 | ❌ | 需加入 EntityRule | 默认始终生效 |
| `ICheckRuleBuilder<T>` | 把"规则逻辑 + 激活条件"打包复用 | ✅ | 需加入 EntityRule | ✅ |
| `@BusinessRule` | 仅标记可视化，不参与运行期校验 | - | - | - |

选择建议：

- **场景是单条简单校验** → `BaseRuleValidator<T>`。
- **场景是多条规则组合、需要动态调整或配置开关** → `EntityRule<T>`。
- **需要参数化违规消息** → 使用返回 `RuleCheckResult.fail(params)` 的 `ICheckRule`（直接写 lambda 或实现 `ICheckRuleBuilder`）。
- **需要给可视化/AI 提供规则元信息** → 额外用 `@BusinessRule` 标记。

下一步建议阅读：

- [领域事件](./domain-events.md)：规则通过后的事件触发
- [应用服务](./application-service.md)：校验在命令执行模板中的位置
- [模型可视化](./model-visualization.md)：`@BusinessRule` 的可视化消费
