# 业务规则引擎（Business Rules）

> 本文档说明 `io.pragmatic.ddd.base` 与 `io.pragmatic.ddd.rules` 包提供的业务规则能力。其中规则顶层抽象 `IRule` 与违规收集（`BrokenRule` / `MessageCode` 等）位于 `base` 包，校验项级契约 `ICheckRule` / `RuleCheckResult` 及其容器实现位于 `rules` 包。相关文档：[领域建模](./domain-modeling.md) · [领域事件](./domain-events.md) · [仓储](./repository.md)。

## 1. 概述

### 1.1 核心定位

业务规则（Business Rules）用于表达聚合内部的**不变量约束**——在聚合生命周期的任意时刻都必须成立的条件（如「发票标题不可为空」「订单金额不得超过信用额度」）。框架将规则建模为可执行单元，在聚合状态变更后由应用层触发校验，违反时收集为 `BrokenRule` 并抛出 `BrokenRuleException`。

设计意图（一句话）：业务规则是聚合保证自身一致性的「守门人」，将校验逻辑从业务方法中剥离为可组合、可插拔、可动态启停的单元。

### 1.2 概念层级与依赖关系

```text
io.pragmatic.ddd.base           契约与基础（规则顶层抽象 + 违规收集）
  ├─ IRule<T>                   规则核心契约（satisfiesRule）
  ├─ MessageCode                record：规则消息码（局部码 + 描述）
  ├─ BrokenRuleRegistry         消息码注册表基类（反射自动注册）
  ├─ BrokenRule / BrokenRuleObject  违反值对象 / 收集器（AggregateRoot 组合持有）
  └─ AggregateRoot<T>           聚合根：satisfiesRule + 抛异常委托

io.pragmatic.ddd.rules          校验项契约 + 容器与扩展实现（依赖 base）
  ├─ ICheckRule<T>              单条不变量校验（check → RuleCheckResult）
  ├─ RuleCheckResult            校验结果（pass / fail / 带参数）
  ├─ EntityRule<T>              实体规则容器（一维规则列表 + failFast）
  ├─ RuleItem<T>                规则项（校验项 + 消息码 + 激活条件）
  ├─ IRuleBuild                 规则生命周期钩子（init / reset）
  ├─ IActiveRuleCondition<T>    激活条件（code 级开关 + 模型级条件）
  ├─ ActiveStatus               激活状态枚举（ACTIVE / INACTIVE）
  ├─ RulePosition               插入位置枚举（LAST / BEFORE / AFTER）
```

### 1.3 类型与包路径

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IRule<T>` | `io.pragmatic.ddd.base` | 规则核心契约（聚合级） |
| `ICheckRule<T>` | `io.pragmatic.ddd.rules` | 单条不变量校验契约 |
| `RuleCheckResult` | `io.pragmatic.ddd.rules` | 校验结果载体 |
| `MessageCode` / `BrokenRuleRegistry` | `io.pragmatic.ddd.base` | 规则消息码与注册表 |
| `BrokenRule` / `BrokenRuleObject` | `io.pragmatic.ddd.base` | 违反值对象与收集器 |
| `EntityRule<T>` | `io.pragmatic.ddd.rules` | 实体规则容器 |
| `RuleItem<T>` / `IRuleBuild` | `io.pragmatic.ddd.rules` | 规则项与生命周期钩子 |
| `IActiveRuleCondition<T>` / `ActiveStatus` / `RulePosition` | `io.pragmatic.ddd.rules` | 激活条件与定位 |

## 2. 核心概念详解

### 2.1 规则契约：IRule 与 ICheckRule

#### 核心契约：`IRule<T>`

```java
public interface IRule<T> {
    boolean satisfiesRule(T model);
}
```

#### 单条校验契约：`ICheckRule<T>`

```java
@FunctionalInterface
public interface ICheckRule<T> {
    RuleCheckResult check(T newModel, T oldModel);   // 双参数：新模型 + 旧模型
    default RuleCheckResult check(T newModel) {
        return check(newModel, null);
    }
}
```

双参数设计使规则成为**无状态纯函数**：需要新旧对比的规则通过 `oldModel` 获取快照，不需要的规则忽略第二参数。`EntityRule` 自身不持有任何 per-call 可变状态，因此可作为单例（如 Spring Bean）在多线程下安全共享。

### 2.2 校验结果：RuleCheckResult

`ICheckRule` 返回 `RuleCheckResult`，全部通过静态工厂创建：

| 工厂方法 | 含义 |
| --- | --- |
| `RuleCheckResult.pass()` | 校验通过 |
| `RuleCheckResult.fail()` | 校验失败，无动态参数 |
| `RuleCheckResult.fail(Object[] params)` | 校验失败，携带消息参数（`String.format` 格式化） |
| `RuleCheckResult.fail(Object[] params, boolean enableFormat)` | 校验失败，可关闭自动格式化 |
| `RuleCheckResult.of(boolean)` | 由 boolean 直接构造（无动态参数） |

`hasParams()` 为真时，聚合根走参数化消息分支，调用 `addParamBrokenRule(code, params, autoFormat)`。

### 2.3 消息码与注册表：MessageCode / BrokenRuleRegistry

#### `MessageCode`

```java
public record MessageCode(String localCode, String description) {
    public static MessageCode of(String localCode, String description);
    public static MessageCode of(String localCode);
    public String code();   // 返回 localCode，作为 map key 与异常 code
}
```

| 规则 | 说明 |
| --- | --- |
| 相等性 | **仅**按 `localCode` 判定（`description` 不参与） |
| `code()` | 返回 `localCode`，业务上以它作为 key |

#### `BrokenRuleRegistry`

构造时反射扫描子类声明的 `static MessageCode` 字段并自动注册。

| 方法 | 说明 |
| --- | --- |
| `register(MessageCode...)` | `protected final`，以 `code()` 为 key 注册 |
| `getRuleDescription(String)` | 按局部码返回描述；未注册返回空串 |
| `createException(String)` | 构造单条规则违反异常 |
| `createExceptionWithParam(String, Object...)` | 构造参数格式化异常（`String.format`） |
| `of(MessageCode...)` | `static` 内联工厂，免建子类 |

#### 关键约束

> **重要约束**：注册表子类**必须**为 `public`（含测试内 public static nested class）。构造函数通过反射 `field.get(null)` 读取 `static MessageCode` 字段；若子类为包级私有，`IllegalAccessException` 被静默吞掉，导致该消息码**未注册**——`getRuleDescription` 返回空串，且 `addBrokenRule` 收集到的描述为空白（仅影响描述文本，不影响 code）。

> **重要约束**：`MessageCode.localCode` 是 map key 与异常 code，**同一注册表内不允许重复**，重复注册后者覆盖前者。

#### 示例代码

```java
public class InvoiceBrokenRuleRegistry extends BrokenRuleRegistry {
    public static final MessageCode TITLE_IS_EMPTY_ERROR =
            MessageCode.of("title_is_empty_error", "title为空");
    public static final MessageCode NO_IS_EMPTY_ERROR =
            MessageCode.of("no_is_empty_error", "编码为空");

    public static final InvoiceBrokenRuleRegistry INSTANCE = new InvoiceBrokenRuleRegistry();
}
```

### 2.4 违反收集：BrokenRule / BrokenRuleObject

`BrokenRule` 是单条违反的值对象：`name`（code）、`description`（消息描述）、可选 `extraData`。

`BrokenRuleObject` 负责规则违反的收集、查询与异常抛出。`AggregateRoot` 以组合方式持有它（注入 `BrokenRuleRegistry` 与 `source`）。关键方法：

| 方法 | 说明 |
| --- | --- |
| `addBrokenRule(MessageCode)` | 追加一条规则违反 |
| `addParamBrokenRule(MessageCode, params, autoFormat)` | 追加带参数的违反（自动 `String.format`） |
| `throwBrokenRuleException()` | 存在违反时抛出**单条**异常（取首条） |
| `throwBrokenRuleAggregateException()` | 存在违反时抛出**聚合**异常（全部违反） |
| `getBrokenRules()` / `clearBrokenRules()` | 查询 / 清空已收集违反 |

### 2.5 规则容器：EntityRule

`EntityRule<T extends AggregateRoot<?>>` 是一个**一维规则列表**，不区分「属性级」与「类级」规则。每条校验项通过 `RuleItem` 封装为「校验项 + 消息码 + 激活条件」。

#### 构造与 failFast

```java
public EntityRule()            { this(true); }   // 默认 failFast=true（遇首条失败即停止）
public EntityRule(boolean failFast) { ... }
```

#### 添加规则

```java
addRule(ICheckRule<T> rule, MessageCode messageCode);
addRule(ICheckRule<T> rule, MessageCode messageCode, IActiveRuleCondition<T> condition);
```

#### 运行时增删改

```java
appendRule(ICheckRule<T> rule, MessageCode appendCode, MessageCode relativeCode,
           RulePosition position, IActiveRuleCondition<T> condition);  // 相对位置插入
replaceRule(ICheckRule<T> rule, MessageCode replaceCode, MessageCode newCode);  // 替换消息码
removeRule(MessageCode messageCode);                                    // 按消息码移除
findRuleByMessageCode(MessageCode) / findRulesByMessageCode(MessageCode...);    // 查询
```

#### 校验执行

`satisfiesRule(T)` 对每个规则项做两重过滤：

1. **code 级开关**：`condition.switchStatus(messageCode) == INACTIVE` 则跳过（读取外部动态配置决定是否启用该规则）。
2. **模型级条件**：`condition.status(model, oldModel) == INACTIVE` 则跳过（基于模型内容 / 新旧对比决定）。

通过后才执行 `check`；失败则按 `hasParams` 选择 `addParamBrokenRule` 或 `addBrokenRule`，`failFast` 为真时立即中止。

#### 单参数适配与新旧对比

| 能力 | 用法 | 说明 |
| --- | --- | --- |
| `EntityRule.of(Function<T, RuleCheckResult>)` | 单参数校验逻辑适配为双参数 `ICheckRule` | 用于不关心旧实体的规则 |
| `requireOldEntity()` 返回 `true` | 子类覆盖，声明需要旧实体 | 不覆盖默认 `false` |
| `supplyOldEntity(T)` | 提供修改前快照 | `satisfiesRule` 每次调用一次 |

#### 关键约束

> **重要约束**：业务规则只校验**聚合自身**的不变量，不发起跨聚合、跨服务的调用。需要外部数据时，应通过参数在构造规则时注入（参考 `InvoiceEntityRule` 注入 `ScoreValidator`），而非在规则内部直接依赖仓储或远程服务。

#### 示例代码

```java
public class InvoiceEntityRule extends EntityRule<Invoice> {
    public InvoiceEntityRule() {
        // 单参数适配：不关心旧实体，lambda 第二参忽略即可
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNotEmpty(s.getTitle())),
                TITLE_IS_EMPTY_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNotEmpty(s.getNo())),
                NO_IS_EMPTY_ERROR);
    }
}
```

### 2.6 激活条件：IActiveRuleCondition

`IActiveRuleCondition<T>` 控制一条规则是否参与校验，有两类职责：

- `status(newModel, oldModel)`：基于**模型内容**判断（纯函数）。
- `switchStatus(messageCode)`：基于**规则标识**判断是否启用（读取配置中心 / 开关平台），默认 `ACTIVE`；既有实现不覆盖即保持原行为。

| 工厂方法 | 说明 |
| --- | --- |
| `IActiveRuleCondition.of(Function<T, ActiveStatus>)` | 仅关心新模型 |
| `IActiveRuleCondition.of(BiFunction<T, T, ActiveStatus>)` | 需要新旧对比 |
| `AlwaysActiveRuleCondition<T>` | 无条件生效的默认实现 |

### 2.7 统一校验项契约：ICheckRule

框架不提供「`validate(newModel, oldModel)` 返回 boolean」的适配基类/构造器，校验项统一通过实现 `ICheckRule<T>` 承载：

```java
public class ScoreValidator implements ICheckRule<Order> {
    @Override
    public RuleCheckResult check(Order newModel, Order oldModel) {
        return RuleCheckResult.of(newModel.getScore() != null);
    }
}
```

外部依赖（如评分校验器 `ScoreValidator`）经构造器注入规则，使规则保持无状态且可单例共享；需自定义激活条件时，通过 `addRule(ICheckRule<T>, MessageCode, IActiveRuleCondition<T>)` 传入即可，无需额外的适配器类型。

### 2.8 聚合根集成：AggregateRoot

`AggregateRoot` 组合 `BrokenRuleObject`，并暴露校验委托方法：

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `satisfiesRule(IRule<?>)` | `public` | 以自身为 model 执行规则，`rule==null` 视为通过；返回 `true`/`false` |
| `addBrokenRule(MessageCode)` | `public` | 追加一条规则违反 |
| `addParamBrokenRule(MessageCode, Object[], boolean)` | `public` | 追加支持参数格式化的违反；`isAutoFormat=true` 时用 `String.format(description, params)` |
| `getBrokenRules()` | `public` | 返回已收集违反（只读） |
| `throwBrokenRuleException()` | `public` | 有违反则抛**单条**异常（取第一条） |
| `throwBrokenRuleAggregateException()` | `public` | 有违反则抛**聚合**异常（含全部） |
| `clearBrokenRules()` | `public` | 清空已收集违反 |

聚合根必须实现抽象方法 `protected abstract BrokenRuleRegistry brokenRuleRegistry();` 以接入消息码注册表。

#### 示例代码

```java
public class Invoice extends AggregateRoot<String> {
    private String title;
    private String no;

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return InvoiceBrokenRuleRegistry.INSTANCE;      // 提供消息码注册表
    }

    public void changeTitle(String title) {
        this.title = title;
    }
    // 业务方法内不内联校验逻辑，交由规则容器统一校验
}
```

## 3. 关键机制与避坑指南

### 3.1 校验两阶段：收集与抛异常分离

> **重要约束**：校验与抛异常是两步操作——先 `satisfiesRule` 收集违反，再 `throwBrokenRuleException` / `throwBrokenRuleAggregateException` 决定抛单条还是聚合异常；不调用抛异常方法就不会中断流程。

```java
Invoice invoice = invoiceRepository.findById(invoiceId);
invoice.changeTitle("");                                  // 触发可能违反不变量的变更
if (!invoice.satisfiesRule(new InvoiceEntityRule())) {    // 收集违反
    invoice.throwBrokenRuleAggregateException();          // 抛聚合异常中断流程
}
```

### 3.2 failFast 与短路

`EntityRule` 默认 `failFast=true`，遇首条失败即停止后续规则；置 `false` 可一次性收集全部违反（适合前端一次性返回所有字段错误）。注意：`BrokenRuleAggregateException` 仅当存在多条违反时才有意义。

### 3.3 带参数消息的格式化

消息描述支持 `String.format` 占位符，校验时传参由收集器自动格式化：

```java
// 注册表：MessageCode.of("amount_exceed_error", "金额 %s 超过信用额度 %s")
this.addRule((s, old) -> RuleCheckResult.fail(
        new Object[]{ s.getAmount(), s.getCreditLimit() }), AMOUNT_EXCEED_ERROR);
// 收集时自动 String.format → 「金额 1200 超过信用额度 1000」
```

### 3.4 新旧对比的一致性边界

需要旧实体的规则必须同时覆盖 `requireOldEntity()` 返回 `true` 并实现 `supplyOldEntity(T)`；`satisfiesRule` 每次调用 `supplyOldEntity` 一次。`oldModel` 为 `null` 时代表「无旧快照」（如新建场景），规则内部应做空判断。

### 3.5 规则容器与一致性边界

> **边界外不变性不由聚合根保证。** 规则只校验聚合自身；跨聚合写操作通过领域事件发布 + 订阅者响应完成，规则内部不得直接依赖其他聚合的仓储或应用服务。

## 4. 异常与错误处理体系

### 4.1 继承关系

```text
RuntimeException
 └─ PragmaticException              所有框架业务异常的抽象基类（RuntimeException 子类）
     └─ RuleException               业务规则校验异常抽象基类
         ├─ BrokenRuleException           单条规则违反（code + message + source）
         └─ BrokenRuleAggregateException  聚合异常，持有 List<BrokenRuleException>
```

### 4.2 异常字段

| 异常类 | 关键字段 | 说明 |
| --- | --- | --- |
| `BrokenRuleException` | `code` (`String`)、`message`、`source` (`transient Object`) | `code` 即消息局部码；`source` 为触发源，不序列化 |
| `BrokenRuleAggregateException` | `exceptions` (`List<BrokenRuleException>`)、`getSource()` | `getSource()` 返回首个子异常的 source |

### 4.3 捕获与映射规范

- 统一兜底：`catch (PragmaticException e)` 可捕获所有框架异常。
- 规则校验：`catch (BrokenRuleException e)` 取 `e.getCode()` 映射为前端错误码；`catch (BrokenRuleAggregateException e)` 遍历 `e.getExceptions()` 返回全部违反。
- `source` 为 `transient`，跨进程/序列化场景勿依赖。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 规则契约 | 实现 `IRule<T>` / `ICheckRule<T>` | 设计为无状态纯函数，可单例共享 |
| 校验结果 | `RuleCheckResult` 静态工厂 | `hasParams()` 决定参数化消息分支 |
| 消息码 | `MessageCode.of(...)` + `BrokenRuleRegistry` | 注册表子类必须 `public`，否则码未注册 |
| 规则容器 | 继承 `EntityRule<T>` | 默认 `failFast`；先 code 开关后模型条件两重过滤 |
| 激活条件 | `IActiveRuleCondition` / `AlwaysActiveRuleCondition` | `switchStatus(messageCode)` 读取外部开关 |
| 聚合根 | `AggregateRoot<T>` + `satisfiesRule` | 校验/抛异常两步分离；只校验聚合自身 |
| 异常 | `PragmaticException` 体系 | `catch (PragmaticException)` 统一兜底；`source` 为 `transient` |

**下一步阅读**

- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `MessageCode` 基础能力
- [领域事件](./domain-events.md)：跨聚合一致性的事件方案
- [仓储](./repository.md)：聚合持久化与版本对账

## 命名规范速查

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 消息码常量 | 大写 + 下划线，语义化后缀 `_ERROR` | `TITLE_IS_EMPTY_ERROR` |
| 注册表类 | `{聚合}BrokenRuleRegistry`，继承 `BrokenRuleRegistry` | `InvoiceBrokenRuleRegistry` |
| 注册表实例 | `{聚合}BrokenRuleRegistry.INSTANCE` | `InvoiceBrokenRuleRegistry.INSTANCE` |
| 规则容器类 | `{聚合}EntityRule`，继承 `EntityRule<{聚合}>` | `InvoiceEntityRule` |
| 校验器类 | `{聚合}{维度}Validator`，实现 `ICheckRule<{聚合}>` | `InvoiceScoreValidator` |
| 外部注入规则 | 构造器入参注入，规则内部不直接依赖仓储/远程 | `new InvoiceEntityRule(scoreValidator, gradeValidator)` |
