---
name: ddd-aggregate-extend
description: 在已存在的 Pragmatic DDD 聚合上做"扩展"：新增业务操作（充血方法 + 操作注册表常量 + 规则容器 addRule + 领域事件 + 应用层 Input/Updater）、为已有操作补充外部校验（领域服务契约 + 规则容器接入）、新增事件订阅、新增子实体/值对象、新增投影副本源。当用户说「给订单加个改地址操作」「补充风控校验」「加个发货事件订阅」「扩展一个 Redis 缓存源」时触发。严格不破坏既有聚合根等同性、不重复注册码、先 recordOperation 后 collectEvent。
---

# ddd-aggregate-extend（聚合扩展）

> 类型：S5（扩展类）
> 版本：v1.0-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）

## 1. 定位

对**已存在**聚合增量修改，不重写整聚合。所有改动遵循既有模式，且**不破坏既有契约（等同性、已注册的 code、既有事件语义）**。

## 2. 输入

- 目标聚合现有代码（必须 Read 现有 `domain/{agg}` 全量，先理解再改）。
- 扩展需求：新增操作 / 补校验 / 加订阅 / 加子实体或值对象 / 加投影源。

## 3. 工作流程（按需求选分支）

### 3.1 新增业务操作

1. 操作注册表 `{Agg}OperationRegistry` 追加 `public static final EntityOperation {OP} = EntityOperation.of("{OP}", "...")`（**code 不与既有重复**，全大写下划线无前缀，声明在最终子类——硬约束 1/2）。
2. 规则注册表（如需新消息码）追加 `MessageCode.of(...)`（localCode 唯一）。
3. 规则容器 `{Agg}Rule` 追加 `addRule(...)`（含激活条件）。
4. 聚合根充血方法：纯赋值，先做对外校验，先 `recordOperation({OP})` 后 `collectEvent(...)`（硬约束 4/5）。
5. 领域事件 `{Agg}{Op}Event extends BaseDomainEvent`（过去式 + buildEvent + 延迟事件写法）。
6. 应用层：独立 `{Op}Input` + `{Agg}{Op}Updater`（`apply` 只转换 + 调充血方法，不校验不持久化不发事件——硬约束 11）+ `OrderWriteService` 新增公开方法走 `execute`。

### 3.2 补充外部校验

- 领域层新增契约 `I{Agg}{Target}Service extends IDomainService`（见 `domain-service.md`）。
- 规则容器 `addRule` 引用契约；应用层 `@Service` 实现 + 注入规则容器（`OrderRuleConfig`）。
- 注解 `@DomainService(category=BUSINESS_RULE, targetName=...)`。

### 3.3 新增事件订阅

- 领域契约 `I{事件}{意图}Handle extends IDomainService, IHandle<T>` + `@DomainService(category=EVENT_SUBSCRIBER)`。
- 应用实现（外部联动经 `IDependency` / 物化经 `源.sync`）+ 注册表 `evtManager.registerSubscriber(别名, 事件.class, 实例)`（**必须显式**，框架不扫 `@Component`——见 `event-subscriber-pattern.md`）。

### 3.4 新增子实体 / 值对象

- 子实体 `extends AbstractEntity<ID>`，挂 `TrackedList` 到聚合根；值对象实现 `IValueObject`（`private final` + 不可变，禁用 `@Data`——见 `value-object.md`）。
- 建表：子实体独立子表 + 逻辑外键索引；值对象并入父表 JSON（见 `aggregate-database-design.md`）。

### 3.5 新增投影副本源

- 按 `projection-design.md` §5 扩展模式：新 `AbstractProjectionSource` 子类 + 新领域源端口 + 新投影 DTO/常量 + 写路径事件订阅；不改既有源。

## 4. 生成规则

- 先看后改：扩展前必 Read 现有同聚合全部相关文件。
- 不重写既有方法体；只追加。
- 新增 code/常量全局唯一；复用既有码视为错误。

## 5. 强制约束

- 硬约束 1/2/4/5/8/11 + E1~E3 全部适用（与 S4 同基准）。
- 不改聚合根既有字段类型（破坏等同性/已持久化数据）；确需改走迁移脚本。
- 注册表子类 public、常量最终子类；事件延迟 vs 即时按 ID 策略（硬约束 6）。

## 6. 检查清单

- [ ] 扩展前已 Read 现有聚合全量
- [ ] 新操作码全局唯一，未破坏既有注册
- [ ] 充血方法先 recordOperation 后 collectEvent
- [ ] 外部校验走领域服务契约，未把 Input 塞进契约
- [ ] 订阅已显式 registerSubscriber
- [ ] 投影源扩展未改既有源
- [ ] `mvn compile` 通过 + S7 评审零阻断

## 7. 防幻觉

- 既有注册表/规则容器/事件名以现有 `domain/{agg}` 源码为准；不确定先 Read，禁止臆造既有码。
