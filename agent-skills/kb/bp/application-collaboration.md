# BP 卡：应用层落地模式（application-collaboration）

> 源：`documentation/best-practices/application-collaboration.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

应用服务层（Application）**编排**领域逻辑：收 Input → 建聚合（`EntityFactory`）/ 改聚合（`EntityUpdater`）→ 规则校验 → 仓储持久化 → 事件发布，全部由 `execute()` 模板统一编排，置于同一事务边界。

- 职责：编排（Factory/Updater/Rule/Repository/EventManager 串起来）。
- 不做：业务规则（聚合根/规则容器）、持久化细节（仓储）、协议转换（UI 层）。
- 两类服务：写 `{Agg}WriteService`（`extends AbstractApplicationService`）走 `execute()`；读 `{Agg}ReadService`（**无基类**，只读 `implements IQueryApplicationService`）。

## 2. 命名与包结构

```text
application/{agg}/
├── {Agg}WriteService.java   # 命令应用服务（外层）
├── {Agg}ReadService.java    # 查询应用服务（外层）
├── input/                   # {Action}Input 业务语义入参
├── factory/                 # EntityFactory（创建场景）
├── updater/                 # EntityUpdater（修改场景）
├── resolver/                # Command → 领域输入适配
├── rule/                    # 规则装配（OrderRuleConfig）
├── service/                 # 领域服务实现
├── action/                  # ICompensableAction（正向/回写/补偿三元组）
├── compensation/            # ICompensationHandler（durable 兜底）
└── subscriber/              # 事件订阅登记
```

| 类型 | 命名 | 示例 |
| --- | --- | --- |
| 命令应用服务 | `{Agg}WriteService` | `OrderWriteService` |
| 查询应用服务 | `{Agg}ReadService` | `OrderReadService` |
| 入参 | `{Action}Input` | `PayOrderInput` |
| 工厂 | `{Agg}Factory` | `OrderFactory` |
| 修改器 | `{Agg}{Action}Updater` | `OrderPayUpdater` |
| 规则装配 | `{Agg}RuleConfig` | `OrderRuleConfig` |

## 3. 落地方式（核心）

### 3.1 WriteService 骨架

继承 `AbstractApplicationService`，**必须显式注入命令执行器与工作单元工厂**（无默认构造器）；`execute(聚合, 规则, 仓储, Consumer<T>)` 四参；每个用例一个公开方法。

### 3.2 execute() 模板五步（固定）

```text
1. domainLogic.accept(aggregateRoot)   执行领域逻辑（Factory 建 / Updater 改）
2. satisfiesRule(rule)                 规则校验，失败抛 BrokenRuleException
3. persistAndDispatch                  事务内 repository.save（覆盖聚合全部表）
4. 事务提交后 publish                   事件发布严格晚于提交
5. clearWorkUnitState()                事件/操作清空（finally，模板内置）
```

> 第 3、4 步事务边界完全由子类 `persistAndDispatch` 钩子决定，模板不感知事务。仓储不管理事务（§1.5）。

### 3.3 创建：EntityFactory（先算后赋）

`EntityFactory<T, C>`：`T create(C command)`；派生属性先经 `IEntityPropertyCalculator` 算出再装配进聚合构造。

### 3.4 修改：加载聚合 + EntityUpdater

`EntityUpdater<T, C>`：`void apply(T, C)` = Input→领域对象转换 + 调充血方法；**不做校验、不持久化、不发事件**（由模板统一）。

### 3.5 预校验：tryExecute → DryRunResult

`tryExecute(...)` 返回 `DryRunResult`（`passed()`/`brokenRules()`），不落库、不发事件。

### 3.6 规则装配：OrderRuleConfig

规则容器构造依赖（领域服务契约/仓储）由 `@Configuration` 显式声明，领域层零 Spring 依赖。

### 3.7 执行器选择

| 执行器 | 语义 | 场景 |
| --- | --- | --- |
| `CommandExecutor` | 同事务落库，提交后直发 | 事件丢失可接受 |
| `OutboxCommandExecutor` | 聚合写 + outbox 同事务，异步 | 跨模块可靠投递/崩溃兜底 |
| `CompensableCommandExecutor`（装饰器） | 装饰上述任一，叠加逆序补偿 | 跨本地事务外部副作用需回滚 |

> 执行器与工作单元工厂**成对匹配**（`CommandExecutor`+`UnitOfWork`、`OutboxCommandExecutor`+`OutboxUnitOfWork`），禁止混用。

### 3.8 读侧 ReadService：不进 execute() 模板

无基类，只 `implements IQueryApplicationService`；注入领域源端口（`IOrderRedisSource`/`IOrderESSource`），选源写死在方法体内（不把 `ProjectionSource` 当入参），完成「查全量 → 裁剪」两跳；**不发布领域事件、不持有写仓储**。

### 3.9 工作单元（多聚合根同事务）

单聚合根用 `execute()`；多聚合根同一事务用 `beginUnitOfWork()`（`AbstractApplicationService` 暴露，底层 `unitOfWorkFactory.get()`）：

```text
阶段一（事务外）validateAndCollect   领域逻辑+校验+汇总事件，任一违反即终止
阶段二（事务内）persistAndCollect    纯 DB 写（save / 落 outbox）
阶段三（事务外）dispatchEvents       统一发布事件
```

- `IUnitOfWork` 实现 `AutoCloseable`，**一律 try-with-resources**（未提交 `close()` 自动清事件防串味）。
- 组合根以 `Supplier<IUnitOfWork>` 承载每次 `get()` 产新实例的原型语义；**不得**直接暴露单例 Bean。
- 避坑：重复 `commit()` 抛 `UnitOfWorkStateException`；参与聚合须带版本，`doUpdate` 校验 `affected rows=0` 抛乐观锁冲突。

### 3.10 可补偿命令（分布式事务补偿）

三要素：①领域层聚合 `requireExternal(requirement)` 声明需求（`IExternalRequirement` record，`code()` 是稳定路由键）②应用层 `ICompensableAction<A, REQ, T>` 实现正向/回写/补偿（无状态单例；从聚合读事实、幂等；`payload` 非空才能开 durable）③仅补偿用例注入 `CompensableCommandExecutor`（装饰 `OutboxCommandExecutor`）。
durable 模式：`pragmatic-ddd.compensation.mode=durable`，每个动作各提供 `ICompensationHandler`，`actionName()` 必须等于需求 `code`；`CompensationRelay` 由 starter 自动挂载。
约束：段序固定（领域收集→正向+回写→校验落库发事件，段三失败逆序不中断补偿）；事务边界复用被装饰者；补偿不替代 Outbox（叠加）；幂等硬约束；内存/durable 二选一。

## 4. 关键机制与避坑

- 业务方法内"先 `recordOperation` 后 `collectEvent`"，顺序颠倒抛 `OperationException`。
- 延迟事件（ID 后生成必用）：`() -> XxxEvent.buildEvent(this)`。
- 事务边界：`@Transactional` 由调用方/执行器负责，仓储不管理事务。
- 异常响应：`BrokenRuleException`/`BrokenRuleAggregateException`/`PragmaticException` 经 `@RestControllerAdvice` 统一映射。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 应用服务写业务规则/状态判断 | 业务逻辑内聚聚合根，校验走规则容器 |
| 绕过 `execute()` 手动编排 | 统一走 `execute()`/`tryExecute()` |
| Updater/Factory 里做校验或持久化 | Updater 只转换+调充血方法 |
| 每方法 new 执行器/事件管理器 | 构造器注入一次复用 |
| 多聚合根仍逐个 `execute()` 提交 | `beginUnitOfWork()` 注册多条目统一 `commit` |
| `UnitOfWork` 当单例 Bean 注入 | 注入 `Supplier<IUnitOfWork>`，`beginUnitOfWork()` 取新实例 |
| 工作单元不用 try-with-resources | 一律 `try (IUnitOfWork uow = ...)` |
| 执行器与工厂语义混用 | 成对匹配，禁止跨语义组合 |
| 外部动作不设补偿 | 声明 `IExternalRequirement`+实现 `ICompensableAction` |
| 可补偿 `compensate` 不幂等 | 外部按业务单号幂等撤销 |
| 可补偿动作直改字段/从入参读事实 | `apply` 只调充血方法，一律从聚合读 |
| 补偿与 Outbox 二选一 | 装饰叠加使用 |
| durable 漏配 `ICompensationHandler` | 每动作提供，`actionName()` 等于需求 `code` |
| 补偿执行器当通用执行器注入基类 | 仅补偿用例注入 |

## 6. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
