# 防跑偏硬约束（Hard Constraints）

> 源：`documentation/best-practices/` 下各篇——`aggregate-identification.md`（约束 13：跨聚合只持 ID）、`enum-value.md`（E1~E3 枚举基础类型）、`aggregate-evolution-design.md` 与 `order-rule-pattern.md`（旧快照机制）、`registry-design.md`（注册表 public / INSTANCE）、`event-modeling.md`（事件过去式 / buildEvent）、`aggregate-design.md`（聚合根规范 / 零基础设施依赖）。本卡为上述篇章的集中蒸馏清单，供 S4 自检与 S7 评审逐条核对。
> 派生时间：2026-10-18 · 框架版本：2.0.0
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

> **约束 6 判定方法**：看**工厂调用构造时传入的 ID 实参**是否为 null（**不看构造签名**——签名有 ID 也可能被传 null）。
> - 工厂 `create` 传入**真实 ID**（构造内已 `setEntityId`，构造期 ID 非 null）→ **情形 ①**，可直接 `collectEvent(XxxEvent.buildEvent(this))`。
> - 工厂 `create` 传 `null` / ID 由 `IIdGenerator` 后置生成（如 `new Coupon(initData, null)`）→ **情形 ②**，必须用延迟事件 `() -> XxxEvent.buildEvent(this)`。
>
> ⚠️ **易错点**：构造签名含 ID 参数 **不等于** ID 已确定（v3 Coupon 即此例：签名 `Coupon(CouponInitData, Long)` 有 ID，工厂却传 `null`）。判定一律以**实参**为准。

## 枚举出入参规则（memory ID 29822425）

框架约定：聚合与出入参层、投影层的枚举处理遵循三条规则，生成代码时**必须遵守**：

| # | 规则 | 适用边界 |
| --- | --- | --- |
| E1 | 出入参（含查询条件对象、应用层 Input）中的枚举值统一转为 `Integer` 类型（不用枚举类型本身作为字段类型） | 聚合与出入参层 |
| E2 | 枚举值的底层类型跟随聚合上的定义：聚合该字段是 `Integer`/数字则用数字，是 `String` 则用 `String` | 聚合与出入参层 |
| E3 | 投影（projection / 读模型）中的枚举类型，统一转换为对应基础类型（`int`/`Integer`/`String` 等），不再使用枚举类型 | 投影层 |

> 注意区分：聚合**领域内部**的枚举值对象（`IEnumValue<Integer, 自身>`，见 `bp/enum-value.md`）仍用 `int` code 持久化；但**暴露给调用方的 Input / 查询条件 / 投影**字段按 E1~E3 用 `Integer`/`String` 基础类型。

## 自检动作（S4 步骤 5）

1. 逐条核对上方 14 条 + E1~E3；
2. 编译验证：`mvn compile`（模块级）通过；
3. 输出「待补事项」：仓储 SQL、Controller、投影/MQ 装配、单测补齐；
4. 提示用户运行规则容器与聚合根单测骨架。
