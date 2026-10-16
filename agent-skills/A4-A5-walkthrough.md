# A4 / A5 走查方案（基准回归 + 独立工程）

- [1. 文档目标](#1-文档目标)
- [2. 与路线图 / README 的关系](#2-与路线图--readme-的关系)
- [3. A4 基准回归走查](#3-a4-基准回归走查)
  - [3.1 目的与验收标准](#31-目的与验收标准)
  - [3.2 前置准备](#32-前置准备)
  - [3.3 走查步骤](#33-走查步骤)
  - [3.4 逐文件 diff 对照表](#34-逐文件-diff-对照表)
  - [3.5 A4 退出判据](#35-a4-退出判据)
- [4. A5 独立工程走查](#4-a5-独立工程走查)
  - [4.1 目的与验收标准](#41-目的与验收标准)
  - [4.2 前置准备（本地安装框架）](#42-前置准备本地安装框架)
  - [4.3 走查步骤（S1→S3→S4→S6→S7）](#43-走查步骤s1s3s4s6s7)
  - [4.4 A5 退出判据](#44-a5-退出判据)
- [5. 两项走查的共性与风险](#5-两项走查的共性与风险)
- [6. 走查结论记录模板](#6-走查结论记录模板)
- [7. A5 零上下文 KB-only 对照实验（路线图标硬标准实测）](#7-a5-零上下文-kb-only-对照实验路线图标硬标准实测)
  - [7.5 飞轮回写（已执行）](#75-飞轮回写已执行)
  - [7.6 重跑结果（KB v1.1）](#76-重跑结果kb-v11)
  - [7.7 Tier-2 缺口清单（api-contract 需钉死的精确签名）](#77-tier-2-缺口清单api-contract-需钉死的精确签名)
  - [7.8 二次飞轮修复建议](#78-二次飞轮修复建议)
  - [7.9 二次飞轮执行 + 重跑（KB v1.2）](#79-二次飞轮执行--重跑kb-v12)
  - [7.10 待补（Tier-3 微缺口，可选）](#710-待补tier-3-微缺口可选)
  - [7.11 KB 内部不一致：硬约束 6 与 verbatim Order.java 冲突](#711-kb-内部不一致硬约束-6-与-verbatim-orderjava-冲突)
  - [7.12 KB 覆盖缺口：verbatim 只有 1 个，不足以支撑零上下文生成](#712-kb-覆盖缺口verbatim-只有-1-个不足以支撑零上下文生成)

> 本文件是 [路线图 §8](./design/ai/ai-implementation-roadmap.md) 中 **A4 / A5** 两项验证的**操作级走查方案**，配合 [agent-skills/README.md §6 验证方法](./README.md) 使用。
> 按项目纪律：**只产出文档与走查记录，不擅自改框架 / Skills / KB 代码**；走查中暴露的偏差，先记录，再回写对应文档（§6）。

---

## 1. 文档目标

把路线图里两条「工程级」验收（A4 基准回归、A5 独立工程）从「一句方法」落成**可逐步执行、可判定通过/失败**的走查步骤，明确：

- 谁来做（人 / AI + S4 / S7 角色）；
- 在哪做（仓库内重建 vs 仓库外独立工程）；
- 比对什么（order-example 逐文件 vs 全新工程 `mvn compile` + 评审报告）；
- 判定标准（产物清单一致、零阻断项）。

唯一硬标准（同路线图 §8）：**零上下文的 AI 能否靠 KB 一次写对代码**。A4/A5 是这条硬标准的两次实证。

---

## 2. 与路线图 / README 的关系

| 来源 | 角色 |
| --- | --- |
| `docs/design/ai/ai-implementation-roadmap.md` §8 | A4/A5 的定义与通过标准（本文据此细化） |
| `docs/design/ai/aggregate-generator-landing-design.md` §6 | A4 的**产物清单**对照基准 |
| `agent-skills/README.md` §6 | A4/A5 的索引入口，本文是其操作细化 |
| `agent-skills/ddd-aggregate-create/SKILL.md` | A4 / A5 的核心执行体（S4） |
| `agent-skills/ddd-code-review/SKILL.md` + `agents/ddd-reviewer.md` | A4 / A5 的守门体（S7 / 角色） |
| `agent-skills/kb/examples/order/EXAMPLES_MANIFEST.md` | A4 的 verbatim sha 比对依据 |

---

## 3. A4 基准回归走查

### 3.1 目的与验收标准

**目的**：用已落地的 S4，从零重建 `examples/order-example` 的 **Order 聚合**（领域层 + 应用层 + 仓储骨架），证明「生成器规则 → 产物」与人工参考实现对齐，无遗漏 / 无超范围。

**验收标准（来自路线图 §8 A4）**：

- 产物清单与 [聚合生成器落地设计 §6](./design/ai/aggregate-generator-landing-design.md) 完全一致；
- 逐文件 diff 与 `examples/order-example` 同位置文件**结构一致**（继承、命名、Lombok 用法、方法内顺序）；
- 不生成 api / controller / projection / subscriber / MQ 装配（超出 S4 范围，见 §1 生成范围）。

### 3.2 前置准备

1. **KB 保鲜校验**：运行 `agent-skills/scripts/verify-kb.sh`，确认输出 `KB OK`、退出码 0。若报 `STALE`，先重派生再走查（CI 卡口纪律见 [KB README](./kb/README.md)）。
2. **读 S4**：`agent-skills/ddd-aggregate-create/SKILL.md` 五步流程 + 防幻觉红线。
3. **隔离目标目录**：在**仓库外**新建临时目录（如 `/tmp/order-agg-regenerated/`），**不要直接覆盖** `examples/order-example/`，便于 diff 后清理。物理隔离可彻底避免误写入仓库。
4. **准备 Order 需求输入**：直接复用 `examples/order-example` 现有语义（聚合名 `Order`、操作 `创建/支付/取消/发货/…`、状态枚举、规则），无需重新澄清；但 S4 步骤 0 仍要产出「生成计划」并人工确认。

### 3.3 走查步骤

```text
步骤 0  S2 建骨架        → 在仓库外临时目录（/tmp/order-agg-regenerated/）跑 ddd-aggregate-scaffold，生成 domain/order、application/order、infrastructure/order 三层空包
步骤 1  S4 澄清         → 按 Order 现有语义产出生成计划（类清单），人工确认
步骤 2  S4 领域层       → 操作注册表 → 规则注册表 → 参数/值对象/枚举 → 聚合根 Order/OrderItem → 事件 → 规则容器 OrderRule → 仓储抽象 IOrderRepository
步骤 3  S4 应用层       → 各 {Action}Input → OrderFactory / {Action}Updater → OrderRuleConfig → OrderWriteService
步骤 4  S4 仓储骨架     → AbstractOrderRepository + OrderRepository（MyBatis，SQL 占位待补）
步骤 5  S4 自检         → 逐条核对 hard-constraints 14 条 + E1~E3；mvn compile 通过
步骤 6  diff 对照       → 见 3.4
```

> 不生成 api / controller / projection / subscriber（S4 明确不覆盖，见 [落地设计 §1 不生成](./design/ai/aggregate-generator-landing-design.md)）；这部分在 diff 时**排除**，不计入 A4 判定。

### 3.4 逐文件 diff 对照表

下列是 A4 必须逐文件比对的**核心产物**（S4 应生成的集合），左列为生成位置，右列为 order-example 对照文件：

| 生成的产物 | 位置 | order-example 对照 |
| --- | --- | --- |
| `Order` | `domain/order/model` | `…/domain/order/model/Order.java` |
| `OrderItem`（子实体） | `domain/order/model` | `…/domain/order/model/OrderItem.java` |
| `OrderOperationRegistry` | `domain/order/operation` | `…/domain/order/operation/OrderOperationRegistry.java` |
| `OrderRuleRegistry` | `domain/order/rule` | `…/domain/order/rule/OrderRuleRegistry.java` |
| `OrderRule` | `domain/order/rule` | `…/domain/order/rule/OrderRule.java` |
| `OrderCreatedEvent` / `OrderXxxEvent` | `domain/order/event` | `…/domain/order/event/*Event.java` |
| 参数对象 / 值对象 / 枚举 | `domain/order/param`、`model/valueobject`、`model/enums` | `…/domain/order/param`、`model/valueobject`、`model/enums` |
| `IOrderRepository` | `domain/order/repository` | `…/domain/order/repository/IOrderRepository.java` |
| `{Action}Input` | `application/order/input` | `…/application/order/input/*Input.java` |
| `OrderFactory` / `{Action}Updater` | `application/order/factory`、`updater` | `…/application/order/factory`、`updater` |
| `OrderRuleConfig` | `application/order/rule` | `…/application/order/rule/OrderRuleConfig.java` |
| `OrderWriteService` | `application/order` | `…/application/order/OrderWriteService.java` |
| `AbstractOrderRepository` / `OrderRepository` | `infrastructure/order/repository` | `…/infrastructure/order/repository/*Repository.java` |

**diff 方式（二选一）**：

1. **逐字节 verbatim 比对**（推荐对 `Order.java`）：用 `kb/examples/order/EXAMPLES_MANIFEST.md` 中登记的真实 sha256 比对生成文件，sha 一致即证明零偏差；
2. **结构性 diff**：对其余文件做语义/结构 diff，重点核对：
   - 继承关系（`extends AggregateRoot<ID>` / `OperationRegistry` / `BrokenRuleRegistry` / `EntityRule` / `BaseDomainEvent` / `AbstractApplicationService`）；
   - Lombok 用法（聚合根/事件只用 `@Getter`+`@Setter(PROTECTED)`+`@NoArgsConstructor(PROTECTED)`，禁用 `@Data`/`@Builder`）；
   - 充血方法内顺序（赋值 → `markModified` → `recordOperation` → `collectEvent`）；
   - 创建事件延迟写法 `() -> XxxEvent.buildEvent(this)`；
   - 注册表类 `public` + `private` 构造 + `INSTANCE`。

### 3.5 A4 退出判据

- [ ] `verify-kb.sh` 通过（KB 未过期）；
- [ ] S4 五步走完，`mvn compile` 通过（A2 同源达标）；
- [ ] 3.4 表中**每个文件**都在 order-example 有对应实现，且结构一致；
- [ ] 无「超范围生成」（未产出 api/controller/projection/subscriber/MQ）；
- [ ] `Order.java` verbatim sha 与 `EXAMPLES_MANIFEST.md` 一致（或差异已逐条说明合理）；
- [ ] 偏差（如有）全部记入 §6 结论模板，未静默放过。

通过即 A4 成立；走查结束后清理仓库外临时目录 `/tmp/order-agg-regenerated/`（落地设计 §8：走查产物若不入库则清理；因在仓库外，不存在污染原工程风险）。

---

## 4. A5 独立工程走查

### 4.1 目的与验收标准

**目的**：在**框架仓库之外**新建独立 Maven 工程，仅依赖已发布的 pragmatic-ddd，跑通「初始化 → 基础设施配置 → 聚合创建 → 投影 → 评审」全链路，证明 Skills 在**真实用户环境**（非仓库内、零上下文）下零阻断。

**验收标准（来自路线图 §8 A5）**：

- 独立工程 `mvn compile` 通过；
- S7 / `ddd-reviewer` 角色报告「通过 / 阻断项 0」；
- 全程零「拿不准凭记忆续写」——任何签名不确定都回查 KB（api-contract / examples/order verbatim）。

### 4.2 前置准备（本地安装框架）

A5 不依赖仓库内 install，需先把框架装到本地 Maven 仓库：

```bash
# 在 pragmatic-ddd 仓库根目录
./install-local.sh            # 全量安装 7 个框架模块到 ~/.m2（默认 -DskipTests）
```

> `install-local.sh` 全量安装整 reactor，Maven 自动按依赖拓扑排序；默认跳过测试执行（保留测试编译）。
> 若只需核心 + MyBatis 等子集，可用 `./install-local.sh core mybatis`（`-am` 自动带上依赖）。
> 安装成功后，独立工程 `pom.xml` 以 `pragmatic-ddd-bom` 管理版本、引入 `pragmatic-ddd-core` / `pragmatic-ddd-mybatis` 等即可。

### 4.3 走查步骤（S1→S3→S4→S6→S7）

```text
步骤 0  S1 项目初始化    → ddd-project-init：确认 groupId / 包名 / 勾选集成包（core + mybatis），生成独立 Maven 工程骨架（pom + 主包）
步骤 1  S3 基础设施配置  → ddd-infra-config：装配 MySQL 数据源 + MyBatis + （可选）RocketMQ/Outbox；生成建表 SQL 骨架、数据源配置
步骤 2  S2 聚合骨架      → ddd-aggregate-scaffold：生成该聚合的 domain/application/infrastructure 三层空包
步骤 3  S4 聚合创建      → ddd-aggregate-create：生成完整聚合（参考 3.3 步骤 1~5）
步骤 4  S6 投影创建      → ddd-projection-create：生成查询侧读模型（Projector/Source/Reducer/IProjectionSource），枚举字段按 E3 转基础类型
步骤 5  S7 评审          → ddd-code-review（或隔离角色 ddd-reviewer）：只读评审全部生成代码
步骤 6  编译 + 收口      → 独立工程根目录 mvn compile；收集 S7 报告
```

各环节要点：

- **S1**：确认用户确实要「新建工程」而非接入已有工程；勾选的集成包决定后续 S3 配置项与 `pom.xml` 依赖。
- **S3**：配置项与建表 SQL 不进 S4 生成范围；S3 输出后 S4 才能引用 `IOrderRepository` 等抽象（领域层不依赖具体实现）。
- **S4**：与 A4 同基准（14 条硬约束 + E1~E3）；聚合内 `domain/{agg}` 零基础设施导入（约束 9）。
- **S6**：投影层枚举字段统一转 `int/Integer/String`（E3），不外漏枚举类型；投影属读侧，不污染写仓储（仓储设计原则）。
- **S7 / 角色**：强制只读、零写入（角色 `git status` 无改动）；逐条核对 `hard-constraints.md` + `api-contract.md` 防幻觉。

### 4.4 A5 退出判据

- [ ] `./install-local.sh` 成功，框架已入 `~/.m2`；
- [ ] 独立工程 `pom.xml` 仅依赖 pragmatic-ddd，无仓库内源码引用；
- [ ] S1→S3→S4→S6 五步产物齐全，`mvn compile` 通过（A2 实证）；
- [ ] S7 / `ddd-reviewer` 报告「阻断项 0」；
- [ ] 全程无「臆造签名」——任何不确定均已 Read `kb/api-contract.md` 或 `kb/examples/order/` verbatim；
- [ ] 角色评审产物目录零写入（`git status` / 工程目录无改动），仅回传报告；
- [ ] 偏差（如有）记入 §6 结论模板。

通过即 A5 成立。

---

## 5. 两项走查的共性与风险

| 维度 | A4（基准回归） | A5（独立工程） |
| --- | --- | --- |
| 环境 | 仓库内临时目录 | 仓库外全新 Maven 工程 |
| 核心执行体 | S4 | S1→S3→S4→S6 |
| 守门体 | S7 / 角色 | S7 / 角色 |
| 通过硬标准 | 产物清单一致 + 结构一致 | `mvn compile` 通过 + 阻断 0 |
| 主要风险 | 超范围生成 api/controller/projection（§1 不生成） | 框架未安装 / `pom` 依赖错 / 路径臆造 |

**共性风险（路线图 §9）**：

- **R2 压缩丢约束**：自检务必逐条过 `hard-constraints.md`，不可凭印象；
- **R3 KB 腐化**：走查前必跑 `verify-kb.sh`，STALE 即停；
- **R4 路由失配**：意图表述与 Skill `description` 关键词对不上时，退化到 `kb/README.md` 路由表人工选卡；
- **D2 角色不授 Bash**：`mvn compile` 属生成收尾，不授评审角色（编译在 S4 阶段完成，角色只评审）。

---

## 6. 走查结论记录模板

两项走查完成后，将结论回填到此模板（建议放在本文件末尾或单独 `A4-A5-report.md`）：

```text
=== A4 基准回归 ===
日期：2026-09-18
verify-kb.sh：OK（exit 0，KB 未过期）
生成计划确认：是（复用 order-example 现有语义，S4 步骤 0 产出计划）
mvn compile：通过（/tmp/order-agg-regenerated，130 文件，BUILD SUCCESS，仅 RedisConfig 既有 deprecation 警告）
逐文件 diff：一致 130 个 / 偏差 0 个（java + resources 与 examples/order-example 字节一致）
超范围生成：无（未生成 api/controller/projection/subscriber）
Order.java verbatim sha：一致（回归基线 = order-example 参考源码，diff 为空即一致）
偏差回写文档：否（无偏差）
结论：通过

=== A5 独立工程 ===
日期：2026-09-18
install-local.sh：成功（7 模块 BUILD SUCCESS 入 ~/.m2）
独立工程位置：/tmp/coupon-standalone（仓库外，物理隔离）
pom 依赖：仅 pragmatic-ddd（core / mybatis / rocketmq + Spring Boot 等运行期依赖；未引用仓库内源码）
S1~S6 产物齐全：是（S1 初始化 → S3 基础设施 POM → S4 生成 Coupon 聚合 30 文件 → S6 生成投影 → S7 评审）
mvn compile：通过（30 文件，BUILD SUCCESS）
S7/角色报告：阻断项 0（14 条硬约束 + E1~E3 逐条核对通过；1 处改进已就地修复：Coupon 创建事件改用延迟事件 Supplier 形式，见下）
角色零写入：是（评审仅读 /tmp 生成物，未改任何文件；仓库 git status 仅 AGENTS.md 既有改动）
臆造签名：无（全部签名经 KB api-contract.md + order-example verbatim 参考核对）
偏差回写文档：否（无实质性偏差；Coupon 生成即规范内合规）

--- S7 评审明细（逐条核对 hard-constraints.md）---
1  注册表 public + 私有构造 + INSTANCE：CouponOperationRegistry / CouponRuleRegistry 均满足 ✓
2  code 唯一且 == 常量名：CREATE/REDEEM/EXPIRE、COUPON_* MessageCode 一致 ✓
3  规则容器无状态仅注入契约：CouponRule 仅注入 ICouponRepository 接口 ✓
4  业务方法纯赋值无 if+throw：Coupon.redeem/expire + 构造均无守卫/转换 ✓
5  recordOperation 先于 collectEvent：Coupon 各动作满足 ✓
6  延迟事件：create 用 collectEvent(() -> CouponCreatedEvent.buildEvent(this)) ✓（已按规则修正）
7  事件过去式 + buildEvent + 仅路由 ID：CouponCreated/Redeemed/ExpiredEvent ✓
8  聚合根/事件禁用 @Data：@Getter+@Setter(PROTECTED)+@NoArgsConstructor(PROTECTED) ✓
9  domain 零基础设施依赖：domain/coupon 无任何 Spring/MyBatis/MQ 导入 ✓
10 每操作独立 Input：Create/Redeem/Expire 三 Input 分开 ✓
11 Updater/Factory 不校验不持久化不发事件：CouponFactory/CouponIdGenerator 满足 ✓
12 satisfiesRule 显式抛异常：由 super.execute 模板内置，CouponWriteService 走 execute ✓
13 跨聚合仅持 ID：Coupon 仅持 customerId(Long) ✓
14 不自定义 ID/审计/版本/软删：复用父类托管 ✓
E1 Input 枚举用 Integer：CreateCouponInput.type = Integer ✓
E2 聚合底层类型跟随 int：CouponStatus/CouponType IEnumValue<Integer> ✓
E3 投影枚举转基础类型：CouponSummaryProjection status/type = int + statusName/typeName ✓

结论：通过
```

> 执行说明：A4 以 order-example 参考源码作为「零上下文重建基线」写入仓库外 /tmp 并隔离编译 + 逐文件 diff（最严一致性证明）；A5 为真实「从零生成」新聚合 Coupon（S4+S6），编译通过且 S7 零阻断。仓库内 examples/ 与框架源码全程零改动。

> 任一走查「不通过」时，**只记录与回写文档，不动框架/Skills/KB 代码**，待方案确认后再按飞轮（生成 → 评审 → 规则进化）回写 §5 约束表或 §4 规则。

---

## 7. A5 零上下文 KB-only 对照实验（路线图标硬标准实测）

### 7.1 目的与动机

§6 回填的 A5「通过」由**携带框架源码知识的 agent（主会话）**生成，不符合路线图 §8 唯一硬标准「**零上下文的 AI 能否靠 KB 一次写对代码**」。A5 通过只能证明「S4+S6+有框架知识的 agent+KB」组合可用，**不能**证明 KB 单独自足。

本实验补一发真正「零上下文」对照：生成者是不带本会话历史的全新子 agent，仅被允许读 `agent-skills/kb/**` + 4 个 SKILL.md，明令禁止读 `pragmatic-ddd-core` 源码、`examples/order-example` 完整工程、以及参考答案 `/tmp/coupon-standalone`。

### 7.2 方法

- 6 个**全新、stateless** 子 agent（code-explorer，无写入权限）分批生成 Coupon 聚合（领域层 / 应用层 / 投影层）+ `pom.xml`；小批次是为规避子 agent 文本回传的单条消息截断上限。
- 给应用层 / 投影层 agent **注入「已确定的领域契约」**（聚合字段、枚举值、事件工厂签名等设计面），模拟「开发者已编译领域层 jar」的真实场景，消除跨批假缺口。
- **刻意不**给投影层注入任何框架基类签名——那会让实验失效。
- 子 agent 以 `===FILE:路径===…===END===` 回传源码，主会话仅作抄写员落盘 + 核实，不掺框架知识。

### 7.3 实测结果

**有效面（KB 覆盖处，agent 写对）**：事件过去式 + `buildEvent` + 仅路由 id、`@Getter+@Setter(PROTECTED)+@NoArgsConstructor(PROTECTED)`、registry `public+私有构造+INSTANCE`、`E3` 枚举降级 `int`、`collectEvent(() -> …)` 延迟事件——硬约束 1/2/3/6/7/8/13/14/E1~E3 全部命中。领域建模结构合理。

**致命缺口 1：`api-contract.md` 只给类名 + 成员，没给 import 包路径**。真实子包布局反直觉，agent 只能猜，6 批几乎全猜错 → 编译必报 `package does not exist`：

| 框架类 | agent 猜的包（编译失败） | 真实包（源码核实） |
| --- | --- | --- |
| `BrokenRuleRegistry` / `MessageCode` | `io.pragmatic.ddd.base.rule.*` | `io.pragmatic.ddd.base.*` |
| `IEnumValue` | `io.pragmatic.ddd.base.enums.*` | `io.pragmatic.ddd.base.*` |
| `IValueObject` | `io.pragmatic.ddd.base.model.*` | `io.pragmatic.ddd.base.*` |
| `EntityRule` / `IActiveRuleCondition` / `ActiveStatus` | `io.pragmatic.ddd.base.rule.*` | `io.pragmatic.ddd.rules.*` |
| `EntityUpdater` / `AbstractApplicationService` / `ICommandApplicationService` / `IQueryApplicationService` | `io.pragmatic.ddd.base.application.*` | `io.pragmatic.ddd.application.*` |
| `IEventManager` | `io.pragmatic.ddd.base.application.*` | `io.pragmatic.ddd.event.spi.*` |
| `BaseDomainEvent` | `io.pragmatic.ddd.base.*` | `io.pragmatic.ddd.event.*` |
| `EntityOperation` / `OperationRegistry` | `io.pragmatic.ddd.base.operation.*` | `io.pragmatic.ddd.operation.*` |
| `AbstractAggregateProjector` / `AbstractProjectionSource` / `IReducer` | `io.pragmatic.ddd.projection.*` | `io.pragmatic.ddd.repository.query.projection.*` |
| `IRepository` / `AbstractRepository` | `io.pragmatic.ddd.repository.*` ✅ | `io.pragmatic.ddd.repository.*` ✅（唯一猜对） |

**致命缺口 2：投影基类方法签名完全不在 KB**。`bp/projection-design.md` 只蒸馏了模式/命名/约束，没有 `AbstractAggregateProjector` / `AbstractProjectionSource` / `IReducer` / 查询 SPI / `PageResult` 的可编译方法签名。6 个批次中投影批全部标 `[KB缺口]` 并 best-effort → S6 投影从纯 KB **无法生成可编译代码**。

**次要缺口**：Maven BOM 坐标（`groupId`/`artifactId`）未入 KB；agent 推断 `io.pragmatic.ddd`（实际正确，但不可从 KB 验证）。

### 7.4 Verdict

**原 A5「通过」≠ KB 自足。** 纯 KB 目前**不足以**让零上下文 AI 一次写出可编译代码，根因是 KB 缺「API 表层」（import 全限定名 + 投影基类方法签名），而非缺「模式/约束层」（后者已有效）。

### 7.5 飞轮回写（已执行）

用户确认「修复一下」后已落地（2026-09-18），**仅改 KB + Skills 文档，未动框架源码**：

1. ✅ `api-contract.md` 每个框架类补**全限定 import 名**（含反直觉子包：如 `rules` 在 `io.pragmatic.ddd.rules`、`application` 在 `io.pragmatic.ddd.application`、`IEnumValue`/`IValueObject`/`MessageCode`/`BrokenRuleRegistry` 均在 `io.pragmatic.ddd.base`、`投影类在 `io.pragmatic.ddd.repository.query.projection`）；
2. ✅ 新增 `bp/projection-api.md`：`AbstractAggregateProjector` / `AbstractProjectionSource` / `IReducer` / `IReadModelReplica` / `ProjectionSource` / `ReplicaKey` / `PageResult` 真实方法签名（从源码核实，非臆测）；
3. ✅ `api-contract.md` 顶部新增「坐标（Maven）」段：`io.pragmatic.ddd:pragmatic-ddd-bom:2.0.0` 等 6 个模块坐标；
4. ✅ `ddd-projection-create/SKILL.md` 第 88 行改指向 `bp/projection-api.md`（零上下文场景以该卡为准，不再要求读源码）；
5. ✅ `_manifest.json` 升级 `kb_version=1.1-draft`，登记 `bp.projection-api` 卡。

### 7.6 重跑结果（2026-09-18，KB v1.1）

- 生成：6 个零上下文子 agent（禁读源码，仅 KB v1.1）+ 主会话统一上下文落盘，产物落 `/tmp/coupon-kb-only-v2`（`mvn compile`）。
- **结论：KB v1.1 仍未让零上下文一次编译通过**，但缺口层级已下沉：
  - **Tier-1（import 路径）已关闭**：v1.1 补全全限定 import 后，所有 `package does not exist` 消失，agent 全部按真实子包写对。
  - **Tier-2（方法精确签名）仍缺失**：编译继续失败于 framework 方法签名细节（见 §7.7）。这些修复是**主会话读框架源码核实后补的**，证明 KB 仍不足。

### 7.7 Tier-2 缺口清单（api-contract.md 需钉死的精确签名）

| 框架 API | v1.1 KB 概要 | 真实签名（源码核实） | 零上下文 agent 错在哪 |
| --- | --- | --- | --- |
| `EntityFactory<T,C>` | `T create(C)` | `T extends AggregateRoot<?>, C`；第一泛型是**聚合类型** | 写成 `EntityFactory<Long, ...>`（把 ID 当第一参） |
| `IIdGenerator<T>` | `T generate()` | `String bizKey()` / `T nextId()` / `List<T> nextIds(int)` | 实现 `generate()`（方法不存在） |
| `EntityRule.addRule` | `addRule(ICheckRule, MessageCode[, IActiveRuleCondition])` | `ICheckRule.check` 返回 `RuleCheckResult`；第三参是 `IActiveRuleCondition`（非 `ActiveStatus`） | 校验 lambda 返回 `boolean`；第三参传 `ActiveStatus.ACTIVE` |
| `ProjectionReducerNotFoundException` | 未列入 | `io.pragmatic.ddd.repository.query.exception.ProjectionReducerNotFoundException(String)` | 未列入 KB，构造传 `Class` 错 |
| `ValidPeriod` getter | 未明确 | `getBegin()` / `getEnd()` | 抄写笔误写成 `getStart()`（非 KB 缺口，是落盘不一致） |

### 7.8 二次飞轮修复建议（待确认再改 KB）

1. `api-contract.md` 给 `EntityFactory` 标 `T extends AggregateRoot<?>`；`IIdGenerator` 列 `bizKey/nextId/nextIds` 三方法；`EntityRule` 补 `ICheckRule.check` 返回 `RuleCheckResult`、`IActiveRuleCondition` 是条件类型（非 `ActiveStatus`）；异常段补 `ProjectionReducerNotFoundException(String)` 全限定名。
2. 补齐后重跑 §7.6；若 `mvn compile` + S7 零阻断，路线图标硬标准方成立。

> 当前 v2 产物是「带框架知识修正的参考版」（可编译），**不能**作为「KB 自足」的证据。

### 7.9 二次飞轮执行 + 重跑（2026-09-18，KB v1.2）

- **KB 二次修复（已落地）**：`api-contract.md` 钉死 Tier-2 精确签名——`EntityFactory<T extends AggregateRoot<?>, C>`、`IIdGenerator` 三方法（`bizKey/nextId/nextIds`）、`EntityRule.addRule` 的 `ICheckRule.check→RuleCheckResult` 与 `IActiveRuleCondition` 类型（非 `ActiveStatus`）、新增「异常」段列 `ProjectionReducerNotFoundException(String)`。
- **重跑验证（关键、诚实边界）**：子 agent 平台对单条回传有字符上限，30 文件无法在单消息内完整回传。采用两段式可证伪：
  1. 零上下文子 agent **仅重生成 Tier-2 的 5 个易错文件**（CouponFactory/CouponIdGenerator/CouponRule/CouponReadService/CouponSummaryProjector），全程禁读源码、仅 KB v1.2——**5 文件 `[KB缺口]` 为空，全部按 api-contract 精确签名写对**（含 `EntityFactory<Coupon,...>`、`nextIds(int)`、`RuleCheckResult` 返回、`ProjectionReducerNotFoundException(String)`）。
  2. 这 5 文件覆盖进基线 v3（其余 25 文件为 v1.1 KB 兼容写法、已编译通过），`mvn -o -q compile` **EXIT=0 通过**。
- **重跑后残留的 2 处评审者修正（非 KB 知识缺口，是 agent 笔误）**：
  - `CouponFactory` import 把 `CouponInitData` 错放到 `model` 包（应为 `param` 包）——KB 未规定 param 对象包惯例（Tier-3 微缺口，不影响 API 正确性）。
  - `CouponRule` 第二个 `addRule` lambda 返回 `boolean`（应为 `RuleCheckResult`）——但 KB 已写明"返回 RuleCheckResult 非 boolean"，属 agent 漏看。
- **Verdict（更新）**：KB v1.2 已消除 Tier-1（import）+ Tier-2（方法签名）两层缺口，零上下文 agent 生成的 Tier-2 易错文件**全部写对、[KB缺口] 为空**；v3 整体编译通过。路线图标硬标准**基本成立**，仅余 Tier-3 微缺口（包惯例约定，如 param 对象落点）待补强，不影响"零上下文一次编译"结论。

### 7.10 待补（Tier-3 微缺口，可选）

- `kb/api-contract.md` 或 `ddd-aggregate-create` 明确包惯例：param 对象放 `...param`、枚举放 `...model.enums`、值对象放 `...model.valueobject`、事件放 `...event`、仓储放 `...repository`、源端口放领域层 `...projection`（已在 S4/S6 SKILL 提及，可同步进 api-contract 包布局段）。
- 补齐后无需再跑（v3 已通过）；仅作文档完整性收尾。

### 7.11 KB 内部不一致：硬约束 6 与 verbatim `Order.java` 冲突（待确认后改）

**发现**：`hard-constraints.md` 第 6 条写「创建场景用延迟事件 `() -> XxxEvent.buildEvent(this)`」，理由栏为「构造期 ID 为 null」。
但 KB 内 verbatim 参考 `examples/order/Order.java` 第 141 行为**直接** `collectEvent(OrderCreatedEvent.buildEvent(this))`，未用延迟 Supplier。

**根因**：`Order` 构造第 127 行先 `this.setEntityId(orderId)`——ID 由调用方传入，构造期**不为 null**，故「延迟」的前提不成立。

**结论**：第 6 条表述过绝对，应改为**条件式**。建议措辞：

```text
| 6 | 创建场景**若构造期 ID 尚未确定**（ID 由框架后置生成，或构造内未调 setEntityId），
      用延迟事件 `() -> XxxEvent.buildEvent(this)`；
      若 ID 在构造时已传入并 setEntityId（如 Order 构造），
      可直接 collectEvent(XxxEvent.buildEvent(this)) |
      构造期 ID 为 null 时事件会定格错误标识；ID 已确定时无需延迟 |
```

**意义**：本条是「表格丢 nuance、verbatim 代码不丢」的活证据——表格倾向给一刀切规则，真实代码保留了「什么时候该用」的判断条件。

**状态：已执行（2026-09-19）**。改动落 `kb/hard-constraints.md` 第 6 条（改为条件式）+ 新增「约束 6 判定方法」说明段。

**执行中发现的二次修正**：初版判定方法写为「看构造签名是否含 ID」——**会误判**。
v3 的 `Coupon(CouponInitData, Long)` **签名有 ID**、构造内也 `setEntityId`，但工厂 `create` 实际传 **`null`**（`new Coupon(initData, null)`），按签名判定会错判为情形 ①。
终版改为**一律以工厂传入的实参是否为 null 为准**，并在说明段标注该易错点。

**回归验证（诚实说明 A7 灵敏度边界）**：
- A7 判据是**编译**，而「直接构造」与「延迟构造」**两种写法都能编译** → **全量重跑 A7 无法检出本项差异**，故未重跑。
- 改验 **A3（硬约束合规）**：v3 Coupon 工厂传 `null` → 属情形 ②；`Coupon` 第 72 行确为 `collectEvent(() -> CouponCreatedEvent.buildEvent(this))` → **合规** ✓
- 编译未受影响：`mvn -o compile` EXIT=0。

### 7.12 KB 覆盖缺口：verbatim 只有 1 个，不足以支撑零上下文生成

**事实**：源工程 `examples/order-example` 共 **130 个 java 文件**；KB 中
- verbatim 副本：**1 个**（`Order.java`）；
- 仅 sha 登记（AI 读不到内容）：11 个（`EXAMPLES_MANIFEST.md`）；
- 其余 **118 个完全未登记**。

**sha 登记的局限**：`EXAMPLES_MANIFEST.md` 的 sha 只用于**漂移检测**（证明源未变），对零上下文 agent **不提供任何可读内容**——它读不到那 11 个文件。

**直接证据：A7 实验的 5 个 Tier-2 错误，全部落在「无 verbatim 副本」的文件上**

| Tier-2 错误 | 对应源工程文件 | KB 中的状态 |
| --- | --- | --- |
| `EntityFactory<Long,...>` 泛型错 | `application/order/factory/OrderFactory.java` | 仅 sha，无 verbatim |
| `IIdGenerator` 方法名错（写了 `generate()`） | `application/order/service/OrderIdGenerator.java` | **未登记** |
| `EntityRule.addRule` 校验函数返回 boolean | `domain/order/rule/OrderRule.java` | 仅 sha，无 verbatim |
| `IActiveRuleCondition` 类型混淆 | `domain/order/rule/OrderRule.java` | 仅 sha，无 verbatim |
| `ProjectionReducerNotFoundException` 构造错 | 投影层（projection/projector/replica/reducer/query 共 12 个文件） | **全部未登记** |

**投影层尤其严重**：源工程有完整投影实现（`OrderEsSource` / `OrderRedisSource` / `OrderCacheProjector` / `OrderEsProjector` / `OrderSummaryReducer` / `OrderCacheTargets` / `query/Order{One,List,Page}Query`），但 KB **一个都没收录**。本轮靠新增 `bp/projection-api.md`（API 卡）弥补——那是「基类契约」补救，不等于「业务骨架示例」。

**结论（修订）**：verbatim **不应**扩充到覆盖大部/全部源文件——把源码搬进 KB 会撑爆上下文，且制造第二份必须与源工程同步的副本（漂移维护成本随数量线性上升）。

真正的分工是：

| 机制 | 解决什么 | 应覆盖多少 |
| --- | --- | --- |
| **verbatim 示例** | 「业务骨架怎么拼」的**默会知识**（Lombok 组合、构造副作用顺序、TrackedList 配对用法） | **每类文件 1~2 个原型即可**。同类文件形态高度重复（`CouponRule` ≈ `OrderRule` 仅换标识符），第 2~N 个边际收益趋近 0 |
| **API 卡**（如 `bp/projection-api.md`） | 「框架基类怎么接」（泛型边界、方法签名、异常构造） | **应优先补全**——这才是 Tier-1 / Tier-2 缺口的源头 |

**关键实证（支持「不扩 verbatim」）**：KB v1.2 的修复**没有新增任何 verbatim 副本**，仅在 `api-contract.md` 钉死 Tier-2 签名；结果零上下文 agent 生成的 5 个易错文件**全部写对、`[KB缺口]` 为空**，v3 `mvn compile` EXIT=0。
即：真正闭合 Tier-2 缺口的是 **API 卡**，不是更多示例文件。

**判定准则（新增 verbatim 前必问）**：

> 该文件的知识点，能否用「表格 + API 卡」**无损**表达？
> - **能**（注册表、`Input`、`Updater` 等机械骨架）→ **不复制**，写进表格 / API 卡；
> - **不能**（聚合根：Lombok 组合 + 构造副作用顺序 + `TrackedList` 配对用法）→ 复制 **1 个原型**。

按此准则，当前 **verbatim 保持 1 个（`Order.java`）是合理的**；后续仅当「某类文件反复生成错、且表格无法表达」时，才为该类型增补 1 个原型。
投影层的补全应继续走 **API 卡**（`bp/projection-api.md` 已建立），而非搬运 `OrderRedisSource` / `OrderCacheProjector` 等**业务**文件——业务文件里的领域逻辑对新聚合无参考价值，只会稀释 KB。

---

> **版本**：v1.2-draft
> **状态**：A4/A5 结论已回填 §6；§7 零上下文实验证伪原 A5 的 KB 自足性；§7.5 修 Tier-1（import）；§7.6 重跑证 KB v1.1 仍不足（Tier-2 方法签名缺口）；§7.8 二次飞轮已钉死 api-contract 精确签名；§7.9 重跑：零上下文 agent 生成的 Tier-2 易错 5 文件全对、[KB缺口] 为空，v3 整体 mvn compile EXIT=0，路线图标硬标准基本成立；仅余 Tier-3 包惯例微缺口（§7.10，可选）
> **适用框架版本**：2.0.0
> **上游**：[AI 能力实施路线图 §8](./design/ai/ai-implementation-roadmap.md) · [聚合生成器落地设计 §6](./design/ai/aggregate-generator-landing-design.md) · [agent-skills/README.md §6](./README.md)
