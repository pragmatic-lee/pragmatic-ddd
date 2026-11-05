# 防跑偏硬约束（Hard Constraints）

> 源：`documentation/best-practices/` 下各篇——`aggregate-structure.md`（约束 15：包落点判据）、`aggregate-identification.md`（约束 13：跨聚合只持 ID）、`enum-value.md`（E1~E3 枚举基础类型）、`aggregate-evolution-design.md` 与 `order-rule-pattern.md`（旧快照机制）、`registry-design.md`（注册表 public / INSTANCE）、`event-modeling.md`（事件过去式 / buildEvent）、`aggregate-design.md`（聚合根规范 / 零基础设施依赖）、`application-collaboration.md`（约束 10 / 11 / 16：应用服务落点与注入白名单）、`event-subscriber-pattern.md`（约束 17：订阅三要素与同聚合绑定）。本卡为上述篇章的集中蒸馏清单，供 S4 自检与 S7 评审逐条核对。
> 派生时间：2026-11-05（新增约束 16 / 17：应用服务层与订阅侧） · 框架版本：2.1.0
> **用途**：S4 生成后自检逐条核对；S7 评审逐条检查。任一条不满足即修正后重新核对。

## 通用红线（所有聚合代码通用）

| # | 约束 | 违反后果 |
| --- | --- | --- |
| 1 | 注册表类必须 `public`，私有构造 + `INSTANCE` 单例 | 反射注册静默失败，违规描述空白 / `OperationException` 延后暴露 |
| 2 | `MessageCode.localCode` / `EntityOperation.code` 在注册表内唯一，且 `code` 与常量字段名完全一致 | 后者覆盖前者，码与描述错位 |
| 3 | 规则容器无状态，仅注入领域服务契约引用 | 破坏单例共享与线程安全 |
| 4 | 业务方法无 `if + throw` 守卫、无数据组装转换，纯赋值 | 校验散落、方法不可测 |
| 5 | 业务方法内先 `recordOperation` 后 `collectEvent` | 顺序颠倒抛 `OperationException` |
| 6 | 创建事件按 **ID 是否已确定**分两种写法：① **ID 构造时已确定**（构造内已 `setEntityId`，ID 由调用方传入）→ 直接 `collectEvent(XxxEvent.buildEvent(this))`；② **ID 构造期尚未确定**（ID 由框架后置生成 / 构造未调 `setEntityId`）→ 用**延迟事件** `() -> XxxEvent.buildEvent(this)` | 情形 ② 写成直接构造 → 事件定格错误标识（ID 为 null）；情形 ① 用延迟写法虽不报错，但无必要 |
| 7 | 事件命名过去式，经 `buildEvent` 工厂构造，不带业务快照 | 语义失真、快照膨胀过期 |
| 8 | 聚合根禁用 `@Data` / `@EqualsAndHashCode` / `@Builder`，用 `@Getter` + `@Setter(PROTECTED)` + `@NoArgsConstructor(PROTECTED)` | 破坏等同性、构造副作用丢失 |
| 9 | `domain/{agg}` 零基础设施依赖，无 Spring / MyBatis / MQ 导入 | 领域层被技术细节污染 |
| 10 | 每个操作独立 `{Action}Input`，不复用同一 DTO | 大对象共用导致字段冗余与空值陷阱 |
| 11 | Updater / Factory 不校验、不持久化、不发事件 | 打断 `execute` 模板顺序 |
| 12 | `satisfiesRule` 收集后必须显式抛异常（`execute` 模板已内置） | 校验通过却不中断，脏数据落库 |
| 13 | 跨聚合只持 ID 引用，不持对象引用 | 加载整个对象图，聚合边界失效 |
| 14 | 不自定义 ID / 审计 / 版本号 / 软删字段 | 与父类托管能力冲突 |
| 15 | 新类的包落点按 `bp/aggregate-structure.md` 落点判据表执行：参数对象（`IParamObject`）落 `domain/{agg}/param/` 不落 `model/`；枚举 `model/enums/`、值对象 `model/valueobject/`、事件 `event/`；应用层 Input 留 `application/{agg}/input/`，即使实现 `IParamObject` 也不入领域层 | 包失去语义导航，生成期只能临场决策（Coupon 走查已因此返工），评审无法机械核对 |
| 16 | 应用服务落点与注入白名单：`{Agg}WriteService` / `{Agg}ReadService` 落 `application/{agg}/` **根层**，不进 `service/`；`service/` 只放实现 `@DomainService` 契约的类；两服务互不注入；写侧不注入投影源 / `Projector` / `Reducer` / ES·Redis 客户端，读侧不注入写仓储 / `IEventManager` / Updater / `WriteService` | 目录语义失效；读写边界消失，读路径可发事件或改聚合，污染写一致性 |
| 17 | 订阅三要素齐备与同聚合绑定：①契约（`I{X}Handle` + `@DomainService(EVENT_SUBSCRIBER, targetName=事件简单类名)`）②实现（`application/{agg}/service/`，注解按 category）③ `subscriber/` 注册行，三者缺一即不成立；`subscriber/` 只放 `{Agg}EventSubscriberRegistry`；注册表只登记本聚合 `domain/{agg}/event/` 的事件与本聚合实现 | 框架不扫 `@Component`，缺注册行启动零报错、事件静默无人消费；注册表越界则退化成跨聚合总线，聚合边界失效 |

> **约束 6 判定方法**：看**工厂调用构造时传入的 ID 实参**是否为 null（**不看构造签名**——签名有 ID 也可能被传 null）。
> - 工厂 `create` 传入**真实 ID**（构造内已 `setEntityId`，构造期 ID 非 null）→ **情形 ①**，可直接 `collectEvent(XxxEvent.buildEvent(this))`。
> - 工厂 `create` 传 `null` / ID 由 `IIdGenerator` 后置生成（如 `new Coupon(initData, null)`）→ **情形 ②**，必须用延迟事件 `() -> XxxEvent.buildEvent(this)`。
>
> ⚠️ **易错点**：构造签名含 ID 参数 **不等于** ID 已确定（v3 Coupon 即此例：签名 `Coupon(CouponInitData, Long)` 有 ID，工厂却传 `null`）。判定一律以**实参**为准。

> **约束 15 的两个易错点**：
> - 判据是「类型 + 所在层」双条件，**不得只看标记接口**：应用层 Input 与领域层参数对象可实现同一个 `IParamObject`，落点仍按层区分。
> - **存量降级**：本条只对新建类 / 本轮新增类判阻断；存量类位置不符 → 评审记建议项（Advisory）不阻断，项目长期保留现状时记入该项目判决名单 `accepted.md`。

> **约束 16 判定方法**：看类声明的实现接口 + 所在包（双条件），不看类名后缀 `Service`；再看构造器参数类型——写侧出现 `*Source` / `*Projector` / `*Reducer` / `*Client` 即违规，读侧出现 `IEventManager` / Updater / 写仓储 / `*WriteService` 即违规。
>
> **约束 17 判定方法**：三要素交叉对齐——有契约无注册、有注册无实现、注册的 `Class<T>` 与 `IHandle<T>` 的 `T` 不一致，三者均判违规；再核事件包是否在本聚合 `domain/{agg}/event/`、实现包是否在本聚合 `application/{agg}/service/`，跨聚合即违规。

## 枚举出入参规则（memory ID 29822425）

框架约定：聚合与出入参层、投影层的枚举处理遵循三条规则，生成代码时**必须遵守**：

| # | 规则 | 适用边界 |
| --- | --- | --- |
| E1 | 出入参（含查询条件对象、应用层 Input）中的枚举值统一转为 `Integer` 类型（不用枚举类型本身作为字段类型） | 聚合与出入参层 |
| E2 | 枚举值的底层类型跟随聚合上的定义：聚合该字段是 `Integer`/数字则用数字，是 `String` 则用 `String` | 聚合与出入参层 |
| E3 | 投影（projection / 读模型）中的枚举类型，统一转换为对应基础类型（`int`/`Integer`/`String` 等），不再使用枚举类型 | 投影层 |

> 注意区分：聚合**领域内部**的枚举值对象（`IEnumValue<Integer, 自身>`，见 `bp/enum-value.md`）仍用 `int` code 持久化；但**暴露给调用方的 Input / 查询条件 / 投影**字段按 E1~E3 用 `Integer`/`String` 基础类型。

## 自检动作（S4 步骤 5）

1. 逐条核对上方全量硬约束（条数以本卡表为准）+ E1~E3；应用服务与订阅相关类另按 16 / 17 的判定方法交叉核对（落点 + 注入白名单 + 三要素齐备）；
2. 编译验证：`mvn compile`（模块级）通过；
3. 输出「待补事项」：仓储 SQL、Controller、投影/MQ 装配、单测补齐；
4. 提示用户运行规则容器与聚合根单测骨架。
