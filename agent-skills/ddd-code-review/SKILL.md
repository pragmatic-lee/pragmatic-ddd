---
name: ddd-code-review
description: 评审 Pragmatic DDD 生成/手写代码的「类合规」——逐条核对硬约束（注册表 public、禁用 @Data、先 recordOperation 后 collectEvent、Lombok 注解、domain 零基础设施依赖、枚举出入参类型、应用服务落点与注入边界、订阅三要素齐备等）与框架 API 契约；提供设计文档时另做「设计符合性对账」（实现是否与 ②③ 一致）。当用户说「检查一下这段 DDD 代码合不合规」「评审聚合代码」「对照设计文档看看实现对不对」「ddd-reviewer 跑一遍」时触发。不做业务正确性判断，只做框架约定与设计一致性评审；发现违规输出阻断项清单。
---

# ddd-code-review（类合规评审，S7）

> 类型：S7（评审类）
> 版本：v2.0 · 适用框架：2.1.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）
> 升级路线：本期作为 Skill；可选升级为隔离角色 `ddd-reviewer`（见路线图 §7.4），评审产物**零写入**业务目录。

## 触发

用户意图 = 评审生成代码是否合规：代码片段、单文件、或整个聚合包。

两类触发：

- 只给代码 → 硬约束评审。
- 同时给 ②③ → 额外做「设计符合性对账」（步骤 5），回答「实现是否与设计一致」。

## 输入

- 待评审代码（文件路径或粘贴片段）。
- **可选：② 写聚合 + ③ 读投影**（取每类 `*-V*.md` 中 V 最大的那份）。提供时做「设计符合性对账」（步骤 5），仅给代码时只做硬约束评审。
- 可选：用户关注的合规维度（默认全量核对）。

## 执行步骤

### 步骤 1：加载评审基准

- Read `kb/hard-constraints.md`（全量硬约束 + E 系列）— 唯一评审基准；包落点判据表（约束 15）在 `kb/bp/aggregate-structure.md`。
- **应用服务层**另读 `kb/bp/application-collaboration.md`（落点 / 读写注入边界 / 反模式）；**订阅侧**另读 `kb/bp/event-subscriber-pattern.md`（三要素 / 别名 / 同聚合绑定）。
- 不确定签名 → Read `kb/api-contract.md`（权威符号表）。

### 步骤 2：逐条核对（结构化输出）

对每条硬约束给出：✅ 满足 / ❌ 违反（附文件:行号 + 修复建议）。

| 维度 | 核对项（来自硬约束表） |
| --- | --- |
| 注册表 | 1 子类 public + 单例；2 localCode/code 唯一且与字段名一致；操作常量类型 `EntityOperation`、声明最终子类 |
| 规则容器 | 3 无状态仅注入契约 |
| 充血方法 | 4 无 if+throw 守卫；5 先 recordOperation 后 collectEvent |
| 事件 | 6 延迟事件写法；7 过去式 + buildEvent + 无快照 |
| Lombok | 8 聚合根/事件禁用 @Data/@Builder，仅 Getter+Setter(PROTECTED)+NoArgsConstructor(PROTECTED) |
| 依赖 | 9 domain 层零 Spring/MyBatis/MQ |
| 出入参与投影 | 10 每操作独立 Input；E1~E3 枚举出入参/投影用 Integer/String 基础类型；E4 投影/条件字段禁用领域值对象/实体/聚合根类型（存量 Advisory） |
| 应用层 | 11 Updater/Factory 不校验不持久化不发事件；12 satisfiesRule 必抛异常 |
| 边界 | 13 跨聚合只持 ID |
| 父类能力 | 14 不自定义 ID/审计/版本/软删 |
| 包落点 | 15 参数对象在 `domain/{agg}/param/`（不在 `model/`）；枚举 `model/enums/`、值对象 `model/valueobject/`、事件 `event/`；Input 仍在 `application/{agg}/input/`（按类型 + 所在层双条件判，不得只看 `IParamObject`） |
| 应用服务落点与注入 | 16 WriteService / ReadService 落 `application/{agg}/` 根层、不进 `service/`；`service/` 只放 `@DomainService` 契约实现；写侧禁投影源 / `Projector` / `Reducer` / ES·Redis 客户端，读侧禁写仓储 / `IEventManager` / Updater / `WriteService`；两服务互不注入 |
| 订阅三要素 | 17 契约（`@DomainService(EVENT_SUBSCRIBER, targetName=事件简单类名)`）+ 实现（`service/`）+ 注册行，缺一即违规；注册的 `Class<T>` 须与 `IHandle<T>` 的 `T` 一致 |
| 订阅同聚合绑定 | 17 注册表只登记本聚合 `domain/{agg}/event/` 的事件与本聚合实现；`subscriber/` 只放 `{Agg}EventSubscriberRegistry`（`IHandle` 实现不放这里） |

### 步骤 3：输出评审报告

- **阻断项（Blocking）**：违反硬约束，必须修复后重新评审。
- **建议项（Advisory）**：可改进但不阻断（如命名风格）。
- **约束 15 的存量降级**：只对新建 / 本轮新增类判阻断；存量类包位置不符 → 记建议项并标「待迁移」，不判阻断（位置不符不破坏编译与运行语义，强制迁移会放大变更面）。若项目明确决定长期保留现状，提示其记入判决名单 `docs/design/aggregates/{agg}/accepted.md`，后续评审按判决不重议。
- 零阻断 → 输出「合规通过」。

### 步骤 4：边界声明（重要）

- 硬约束评审**只做框架约定合规**，不做业务逻辑正确性、性能、安全评审。
- 不修改用户代码；修复建议交回用户或 S4 重生成。
- 隔离角色模式下：评审产物写入临时区，**零写入**业务源码目录（路线图 §7.4）。

### 步骤 5：设计符合性对账（提供 ②③ 时执行）

未提供设计文档时跳过，并明说「未提供设计文档，仅做硬约束评审」。

| 对账项 | 基准 | 核对内容 |
| --- | --- | --- |
| 操作码 | ② §3.1 / §3.2 | 常量数、code、字段名与文档一致 |
| 规则 | ② §4.4 | 条数、消息码、激活条件、判定基准（组 C 是否读 `old`） |
| 事件 | ② §5.1 | 类名、载荷字段、即时 / 延迟 |
| 表与列 | ② §7.3 | 实际 DDL 与文档一致；两者不一致时**以实际库为准**并标待确认 |
| 读源 | ③ §2.2 / §4 | 副本标识、查询族与实现一致 |

输出：符合 / 偏离（附文档节号 + 代码位置）。阻断级偏离与步骤 3 合并处理。

本节只能发现「写了但和文档不一样」；「文档本身就不对」不在本 Skill 职责内。

## 防幻觉

- 评审依据：`kb/hard-constraints.md`（红线基准）+ `kb/api-contract.md`（符号权威）+ `kb/bp/aggregate-structure.md`（包落点判据表）+ `kb/bp/application-collaboration.md`（应用服务落点与注入边界）+ `kb/bp/event-subscriber-pattern.md`（订阅三要素与同聚合绑定）；不引用框架不存在的约定。
- **阻断只按硬约束表判**（条数以卡为准）；bp 卡与判据表里更细的表述用于**给修复建议**，不额外升格为阻断项。
- 拿不准某条约束是否适用 → 标记「待人工确认」，不臆断违规。

## 验收（对照路线图 §8）

- A3 逐条核对全量硬约束（含 16 / 17）+ E 系列，零遗漏、零误判、零幻觉符号。
- 已知违规代码对照「角色评审 vs Skill 评审」阻断项召回率（阶段 3 可选）。
