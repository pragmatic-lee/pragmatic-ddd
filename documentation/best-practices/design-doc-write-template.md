# 聚合写设计文档模板（写模型）

> 落地路径：`docs/design/changes/active/{需求}/{agg}/aggregate-design.md`；需求归档时随目录移到 `changes/archive/{需求}/{agg}/`。
> 上游：同目录 `design-doc-semantics`（语义文档）→ 本模板（写侧）→ `design-doc-read-template`（读侧）。
> 交付节奏：G3 闸门后才落笔——上游语义已标「已确认」，且用户明确要求出写设计；闸门与回流见 `design-doc-template` §5.1、§3.1。
> 本模板产出**设计产物**，不是设计说明书：只写结论与产物，不写推导、取舍、代价比较。
> 一次性契约：实施完成即归档，当前实际形态由「聚合事实」快照承接。

## 1. 这份文档是什么

### 1.1 定位

三契约中的第二份，把① 本次需求语义翻译成可实施的技术形态。

| 文档 | 性质 | 份数 | 结束方式 |
| --- | --- | --- | --- |
| ① 本次需求语义 | 契约 | 每需求一份 | 验收后归档 |
| **② 聚合写设计（本模板）** | 契约 | 每需求一份 | **实施后归档** |
| ③ 投影读设计 | 契约 | 每需求一份 | 与②同批归档 |
| ④ 聚合事实 | 快照 | 1 份 | 永不归档，从代码推导 |

本份输入的基线是 ④（当前态）+ ①（本次变更），不是只有 ①。

### 1.2 生命周期

| 项 | 约定 |
| --- | --- |
| 用途 | 本次需求的实施依据 |
| 归档时点 | 实施完成、§10.6 回填栏填完之后 |
| 归档后事实来源 | 由「聚合事实文档」承接，本文档不再更新 |
| 归档前必做 | 填 §10.6，核实并回填 §10.4 |
| 归档后状态 | 只读，不再修改；发现问题走事实文档与代码修复 |

### 1.3 与语义文档的推导关系

推导方向是**单向**的：语义文档 → 设计文档。设计文档可以新增技术形态，但不能改写业务事实。

发现上游缺东西时按 `design-doc-template` §3.1 的回流矩阵处理（唯一权威，本文档不重复定义）。本文档只负责**登记**：

| 登记处 | 装什么 |
| --- | --- |
| §10.1 设计增量 | 语义没写、但属技术形态的（B5 / B9） |
| §10.2 待回流 | 语义没写、但属业务口径的（B1–B4、B6–B8、B10），逐条标「已回 ① vN / 未回」 |
| §10.3 业务待确认 | 设计过程中冒出、归属未定的业务问题 |
| §10.6 实施回填 | 实施与设计不一致（属代码，不回流） |

回流不在本文档里完成：先改 ① 并定稿，再回本文档对齐。

### 1.4 事实源边界与追溯

| 内容 | 唯一出处 |
| --- | --- |
| 职责、属性、动作、事件、规则文案、状态、依赖、查询场景 | 语义文档 |
| 操作码、消息码、类结构、表结构、Mapper、执行顺序、业务订阅 | 本文档 |
| 投影结构、投影器、物化与清除、副本订阅接线 | 投影读设计文档 |
| 设计增量 | 本文档 §10.1 |
| 待回流项（回 ①） | 本文档 §10.2 |
| 实施偏差 | 本文档 §10.6 |

**元素级追溯**：逐行列出语义条目 → 技术落点，覆盖每个动作、规则、事件、状态维度、依赖，一行都不能省。

### 1.5 本次类型

| 类型 | 判定信号 | 额外要求 |
| --- | --- | --- |
| 新建聚合 | 无既有聚合类与表 | 从 §2 静态结构起完整展开 |
| 加聚合 | 本次新增一个聚合，不动既有 | 同上，另在 §10.1 记录与既有聚合的边界 |
| 改既有聚合 | 既有聚合新增动作 / 规则 / 字段 | 先改语义文档；本文档额外写清「替代上一版契约的哪些章节」，三层根因写入 `aggregate-evolution-design`，不进本文 |
| 纯技术改动 | 无语义变更（索引、日志、类型调整、性能优化） | 可跳过 ①，本节标「纯技术，无语义变更」并在 §10.1 写清无业务影响 |

## 2. 两种输入源

### 2.1 标准路径：从语义文档推导

前提：已有一份按 `design-doc-semantics` 产出的语义文档，且已人工确认。

| 语义章节 | 推导到 | 推导方式 |
| --- | --- | --- |
| 1 职责与边界 | §1.2、§2 类划分 | 一句话引用；边界决定依赖 |
| 2 结构与属性 | §2、§7 | 属性 → 字段 → 列；子实体 → 子表 |
| 3 业务动作 | §3.1、§6 | 动作 → 操作码 → 充血方法 → Input/Updater/用例 |
| 4 领域事件 | §5、§6.8 | 事件 → 类 → 载荷 → 订阅方 |
| 5 业务规则 | §4 | 规则 → 消息码 → 激活条件 → 判定方法 |
| 6 状态与流转 | §3.3、§3.4 | 维度 → 枚举；矩阵 → 允许矩阵 |
| 7 外部依赖 | §6.7、§6.2 | 依赖 → 契约 / 补偿 / 计算器 |

### 2.2 降级路径：无语义文档时

先补一份最小语义文档（至少覆盖职责、动作、事件、规则、状态、查询场景），再写设计文档。没有语义文档时，「动作」与「用户故事」会混在一起，规则会散落在 Controller 与 Service 里，设计文档无法收敛。

### 2.3 前置条件

动笔前核对 `design-doc-prerequisites` 的清单。未确认项对应的章节只写结构与占位，不写结论。

**启动条件**（两条都要满足，缺一不落笔）：

1. 上游语义已标「已确认」，且本文档头部已写「基于：① vN」；
2. 用户明确要求出写设计（G3 已过）；未点名时先问，不自行开写。

## 3. 固定目录

11 节，子节与顺序固定。标注「条件」的子节在聚合不适用时**保留标题、不重编号**，正文写一行「本聚合不适用：{原因}」，确保跳章引用（如 §6.7）永远指向同一件事。

```
1. 开始
   1.1 文档定位与生命周期
   1.2 聚合职责
   1.3 与语义文档的推导关系
   1.4 事实源边界与追溯
   1.5 本次类型（条件）
2. 领域模型静态结构
   2.1 包布局
   2.2 类清单
   2.3 聚合根
   2.4 子实体（条件）
   2.5 值对象与枚举
3. 状态与操作设计
   3.1 操作清单
   3.2 操作注册表
   3.3 允许矩阵（条件）
   3.4 状态迁移图（条件）
   3.5 跨聚合一致性强度（条件）
4. 业务规则设计
   4.1 规则注册表
   4.2 外部校验契约（条件）
   4.3 规则容器
   4.4 覆盖对照
   4.5 旧快照判定的范围
5. 领域事件设计
   5.1 事件清单
   5.2 业务事件骨架
   5.3 数据同步事件
6. 应用层设计
   6.1 Input
   6.2 工厂
   6.3 Updater
   6.4 写服务
   6.5 规则装配
   6.6 执行顺序
   6.7 外部依赖与补偿（条件）
   6.8 事件订阅登记（条件）
7. 聚合持久化（关系表）设计
   7.1 表
   7.2 DDL
   7.3 映射与 TypeHandler
   7.4 Mapper 要点
   7.5 持久化约束
8. 改动清单与落地顺序
9. 测试清单
   9.1 规则单测
   9.2 测试夹具
   9.3 聚合行为单测
   9.4 场景测试
   9.5 仓储集成测试
10. 与语义文档的对照、风险与回填
    10.1 设计增量
    10.2 待回流登记
    10.3 业务待确认
    10.4 框架签名
    10.5 设计风险
    10.6 实施回填
11. 附录
    11.1 参考资料
    11.2 与语义文档的对应
```

## 4. 逐节规约

### 4.1 §1 开始

**目的**：让读者在 30 秒内知道这份文档的效期、边界与追溯方式。

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 1.1 文档定位与生命周期 | 用途 / 归档时点 / 归档后事实来源 / 归档前必做 | 4 行表 |
| 1.2 聚合职责 | 一句话 | 段落 |
| 1.3 推导关系 | 四种情况的处理 | 4 行表 |
| 1.4 事实源边界与追溯 | 边界表 + 元素级追溯表 | 2 张表 |
| 1.5 本次类型 | 新建 / 加聚合 / 改聚合 / 纯技术 | 3–4 行表（新建可省） |

**硬规则**

- 生命周期表四项缺一不可，这是归档文档的存在前提。
- 追溯表必须覆盖**每一个**动作、规则、事件、依赖、状态维度。
- 职责只写一句，不展开——展开就与语义文档重复。

**反例**：只写「上游文档：xxx」，不写效期与归档去向。

### 4.2 §2 领域模型静态结构

| 子节 | 固定内容 | 代码形式 |
| --- | --- | --- |
| 2.1 包布局 | 四层目录树 | 代码块 |
| 2.2 类清单 | 类 / 层 / 职责 / 来源 | 表 |
| 2.3 聚合根 | 完整类 | 完整代码 |
| 2.4 子实体 | 完整类 | 完整代码（无子实体时保留标题写「无」） |
| 2.5 值对象与枚举 | 1 个值对象示例 + 成分表；1 个枚举示例 + 取值表；参数对象说明 | 示例 + 表 |

**硬规则**

- 聚合根禁用 `@Data` / `@EqualsAndHashCode` / `@Builder`，用 `@Getter` + `@Setter(PROTECTED)` + `@NoArgsConstructor(PROTECTED)`。
- 业务方法纯赋值，不写 `if + throw` 守卫、不做数据组装。
- 业务方法内先 `recordOperation` 后 `collectEvent`。
- ID 在构造期已确定则用即时事件，未确定才用延迟事件 `() -> XxxEvent.buildEvent(this)`，并在 §5.1 说明判定依据。
- 值对象继承 `ValueObject` 并覆写 `equalityComponents()`；枚举实现 `IEnumValue<整数, 自身>` 并给 `of()` 反查。
- 类清单必须与包布局一致，数量对得上。

**反例**：聚合根字段写 Javadoc 复述字段名；业务方法里 `if (!x) throw`。

### 4.3 §3 状态与操作设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 3.1 操作清单 | 操作码 / 业务动作 / 产生事件 | 3 列表 |
| 3.2 操作注册表 | `{Agg}OperationRegistry` 完整类 | 完整代码 |
| 3.3 允许矩阵 | 动作 × 状态，只标「允许 / ✗」 | 表 |
| 3.4 状态迁移图 | **每个状态维度一张** `stateDiagram-v2` | 图 |
| 3.5 跨聚合一致性强度 | 动作 / 一致性要求 / 保障方式 | 3 列表（无跨聚合影响时可省） |

**硬规则**

- 3.1 不写状态变化与业务含义（在语义文档），只补操作码与事件归属。
- 3.3 不写条件，条件统一去 §4.4 看；终态用 ✗。
- 多状态维度必须每个维度一张图，不得合并成一张。
- 迁移图中节点用中文状态名，边标签用操作码；预留态单独标注。
- 3.5 只写结论：强一致 / 最终一致；最终一致的必须点明靠事件还是靠对账收敛。

**反例**：3.3 里写「允许（需支付状态=待支付）」，与 §4.4 重复。

### 4.4 §4 业务规则设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 4.1 规则注册表 | `{Agg}RuleRegistry` 完整类 | 完整代码 |
| 4.2 外部校验契约 | `I{Agg}{目标}Service` | 完整代码（无则写「无外部校验」） |
| 4.3 规则容器 | 组 A / B / C 各 1 条示例 + 关键判定方法 | 部分代码 |
| 4.4 覆盖对照 | 编号 / 规则 / 消息码 / 组 / 激活条件 / 读取字段 | 6 列表 |
| 4.5 旧快照判定的范围 | 规则 / 被哪个操作改写 / 不读 old 的后果 | 3 列表 |

**硬规则**

- 规则分组固定三类：组 A 全局不变量（总是校验）、组 B 准入（`hasOperation(PLACE)`）、组 C 状态前置（`hasOperation(动作)`）。
- 组 C **全部读 `old`**；容器覆写 `requireOldEntity()` 返回 `true`，`supplyOldEntity` 走仓储 `findById`。
- 规则容器无状态，只注入契约与仓储；不注入 RPC / 事件管理器。
- 4.4 的「读取字段」列必须写明是 `old.x` 还是当前态。
- 消息码与常量名完全一致，全大写下划线。

**反例**：把 15 条 `addRule` 全量贴出来（应与 §4.4 表二选一）。

### 4.5 §5 领域事件设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 5.1 事件清单 | 事件 / 载荷 / 构造（即时/延迟）/ 归属操作 / 影响副本 | 5 列表 |
| 5.2 业务事件骨架 | 1 个完整示例 + 一句「其余同构」 | 部分代码 |
| 5.3 数据同步事件 | `{Agg}DataSyncEvent` 完整类 | 完整代码 |

**硬规则**

- 事件只带聚合标识与少量路由字段，不带业务快照。
- 每个事件必须有归属操作；无归属的只能是数据同步事件。
- 载荷写字段名，不写「订单信息」这类笼统词。
- 「影响副本」列写 `es` / `redis-cache` / 无；该列让读者一眼看出哪个事件驱动哪份投影，具体接线见读设计文档。
- 数据同步事件必继承 `BaseDataSyncEvent`，工厂为 `upsert` / `purge`，`PURGE` 只由 `triggerDataSyncHook` 删除分支产生。
- 5.1 必须给出「即时 / 延迟」的判定依据。

**反例**：事件类里塞 10 个业务字段；`buildEvent` 里拼零散参数。

### 4.6 §6 应用层设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 6.1 Input | 字段表 + 1 个完整示例 | 表 + 代码 |
| 6.2 工厂 | `{Agg}Factory` 完整类 + ID 生成约定 | 完整代码 + 表 |
| 6.3 Updater | Updater 表（有派生属性时加 1 个示例） | 表 |
| 6.4 写服务 | 1–2 个完整方法 + 用例差异表 + **执行器选型表** | 部分代码 + 2 张表 |
| 6.5 规则装配 | `{Agg}RuleConfig` 完整类 | 完整代码 |
| 6.6 执行顺序 | 5 步表 + 一个用例的时序文本 | 表 + 代码块 |
| 6.7 外部依赖与补偿 | 环节表 + 动作骨架 | 表 + 部分代码 |
| 6.8 事件订阅登记 | 订阅表 + `{Agg}EventSubscriberRegistry` | 表 + 完整代码 |

**硬规则**

- Input 一律 `@Data implements IParamObject`；枚举字段用 `Integer`；不引用领域值对象。
- 工厂「先算后赋」：先取 ID、先算派生值，再构造聚合。
- Updater 只做转换 + 调充血方法，不校验、不持久化、不发事件；需要派生值的 Updater 构造注入计算器。
- **执行器选型必须有表**：哪个用例用 `CommandExecutor` / `OutboxCommandExecutor` / `CompensableCommandExecutor`，执行器与工作单元工厂成对匹配。
- 一个工作单元只允许记一个操作；需要两个操作的场景必须拆用例。
- 订阅登记只登记**业务订阅**；投影侧订阅由读设计文档承载。

**反例**：写服务里手写事务与事件发布；补偿动作的回写与主操作同工作单元。

### 4.7 §7 聚合持久化设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 7.1 表 | 表 / 内容 / 主键 / 逻辑外键 | 表 |
| 7.2 DDL | 完整建表语句 | SQL |
| 7.3 映射与 TypeHandler | 字段 → 列 → 通道 + 装配代码 | 表 + 代码 |
| 7.4 Mapper 要点 | 4 个片段：resultMap / CAS / 差量 / 删除 + 差量顺序 | XML + Java |
| 7.5 持久化约束 | 无外键 / 版本 / 差量语义 / 软删 | 4 条列表 |

**硬规则**

- 表边界 = 聚合边界；一个聚合根主表 + 子实体子表，值对象进 JSON 列。
- 主表必有 `version` 乐观锁列、四审计列、软删列；子表不设 `version`。
- 无物理外键；逻辑外键建普通索引且不映射进子实体。
- `doUpdate` 必须判 `affected == 0` 抛乐观锁异常。
- **子表差量固定先 DELETE 后 INSERT**（合并与改数量会产生同 id 的先删后插）。
- resultMap 必须映射 `entityDelete` 到软删列。
- 7.4 只写最容易错的四处，不贴完整 XML。

**反例**：子表整表 `DELETE + 全量 INSERT`；UPDATE 不带 `version` 条件。

### 4.8 §8 改动清单

| 列 | 内容 |
| --- | --- |
| 步 | 序号，按依赖排序 |
| 改动 | 一句话 |
| 依赖 | 前序步骤号 |
| 产出 | 类 / 文件 |
| 可编译 | 是 / 否 |
| 配单测 | 是 / 否 |

**硬规则**

- 每一步结束都必须可编译。
- 必须标出哪些步可并行。
- 读侧只占一行，写明「见读设计文档」。

### 4.9 §9 测试清单

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 9.1 规则单测 | 场景 / 触发操作 / 期望消息码 | 表，一条规则一行 |
| 9.2 测试夹具 | 如何造 `old` 快照 | 代码 |
| 9.3 聚合行为单测 | 关键行为断言 | 列表 |
| 9.4 场景测试 | 场景 / 步骤 / 断言 | 表 |
| 9.5 仓储集成测试 | 增删改、乐观锁、差量、软删 | 列表 |

**硬规则**

- 规则单测必须按消息码断言，不能只断言「抛异常」。
- 必须有一条「新建时组 C 不参与校验」的回归，防止 `old == null` 误拦。
- 必须说明 `old` 快照怎么来（mock 仓储 `findById`）。
- 场景测试至少三条：主流程、反向流程、含外部依赖的流程。

### 4.10 §10 对照、风险与回填

| 子节 | 固定内容 | 硬规则 |
| --- | --- | --- |
| 10.1 设计增量 | 增量 / 语义依据 / 说明 | 语义里没有的都必须列 |
| 10.2 待回流登记 | 缺口 / 回流去向（回 ① 哪条 R·Q·动作·载荷）/ ① 已补版本 / 状态 | 每条给出「已回 ① vN / 未回」，不得停在「已修正」这种自说自话 |
| 10.3 业务待确认 | 事项 / 方案 A / 方案 B / 建议 | 不擅自决定，留给人拍板 |
| 10.4 框架签名 | 符号 / 用途 / 待确认 / **实施回填** | 未登记于 api-contract 的都在这里 |
| 10.5 设计风险 | 风险 / 触发条件 / 应对 | 已闭环的标注闭环位置 |
| 10.6 实施回填 | 设计原文 / 实施实际 / 偏离原因 / 回填人日期 | 归档前必填 |

**硬规则**

- 10.1 与 10.2 是归档后最常被查的两张表，不能省。
- 10.4 不留悬案：实施时核实后必须回填，不允许归档时仍是空格。
- 10.5 里已解决的问题写「已闭环 + 位置」，不留在「应对」栏。
- 10.6 为空不得归档。

### 4.11 §11 附录

| 子节 | 内容 |
| --- | --- |
| 11.1 参考资料 | 读过的 kb 卡清单 + 本文引用的 best-practices 链接 |
| 11.2 与语义文档的对应 | 语义章节 → 本文档位置 |

## 5. 代码形式规约

### 5.1 每类对象给多少代码

固定比例，所有聚合一致：

| 对象 | 代码量 | 其余怎么表达 |
| --- | --- | --- |
| 聚合根 | 完整类 | — |
| 子实体 | 完整类 | — |
| 值对象 | **1 个完整示例** | 成分 / 存储形态表 |
| 枚举 | **1 个完整示例** | 取值表 |
| 事件 | **1 个完整示例** | 载荷 / 构造 / 归属表 |
| Input | **1 个完整示例** | 字段表 |
| 参数对象 | 字段说明 | 表 |
| 操作注册表 | 完整类（本身短） | — |
| 规则注册表 | 完整类（本身短） | — |
| 规则容器 | **组 A/B/C 各 1 条示例 + 判定方法** | 覆盖对照表 |
| Updater | 表 | 有派生属性时 +1 示例 |
| 写服务 | **1–2 个方法** | 用例差异表 |
| 规则装配 | 完整类（本身短） | — |
| 订阅登记 | 完整类（本身短） | 订阅表 |
| DDL | 完整 | — |
| Mapper | **4 个片段** | — |

**判定法**：写每一行代码前问一句「不写，实施者会不会写错？」会写错才写，只是懒得打就不写。

### 5.2 骨架写法约定

| 对象 | 固定写法 |
| --- | --- |
| 聚合根 | `@Getter` + `@Setter(AccessLevel.PROTECTED)` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)` |
| 值对象 | 同上 + `extends ValueObject` + `equalityComponents()` |
| 枚举 | `implements IEnumValue<Integer, 自身>` + `getValue()` / `getName()` / `of(Integer)` |
| 注册表 | `public class` + `private` 构造 + `public static final INSTANCE` |
| 事件 | `@Getter` + `@Setter(PROTECTED)` + `@NoArgsConstructor(PROTECTED)` + `buildEvent` |
| 充血方法 | 字段赋值 → `markModified`/`markCreated` → `recordOperation` → `collectEvent` |
| 工厂 | 取 ID → 算派生值 → 组装 `{Agg}InitData` → `new {Agg}(data, id)` |
| Updater | `apply` 只做转换 + 调充血方法 |

### 5.3 语义标记

聚合根的公有业务方法、规则容器的关键方法、事件类，在 Javadoc 末尾加一行语义标记：

```java
/**
 * 记录支付结果：只推进支付状态，不改物流与订单状态。
 * <p>语义 3.1 支付；语义 5 R11。
 */
```

归档后这是唯一能回答「为什么这么写」的线索。

### 5.4 占位符与编号

| 占位符 | 含义 | 示例 |
| --- | --- | --- |
| `{module}` | 模块包前缀 | `com.acme.order` |
| `{agg}` | 聚合包名，小写 | `order` |
| `{Agg}` | 聚合类名，大驼峰 | `Order` |
| `{Action}` | 动作名，大驼峰 | `Pay` |
| `{AGG}` | 消息码前缀，全大写 | `ORDER` |

| 编号 | 规则 |
| --- | --- |
| 规则 | `R1`…`Rn`，与语义文档一致，不重排 |
| 操作码 | 全大写下划线，不带聚合前缀 |
| 消息码 | `{AGG}_{语义}`，与常量字段名完全一致 |
| 订阅别名 | kebab-case `{动作}-on-{事件}` |

## 6. 聚合特征矩阵

| 特征 | 判定信号 | 影响的子节 | 无此特征时的写法 |
| --- | --- | --- | --- |
| 有子实体 | 聚合内有 `AbstractEntity` 子类 | 2.4、7.1 子表、7.4 差量 | 2.4 写「本聚合无子实体」 |
| 有状态机 | 存在状态枚举字段 | 3.3、3.4 | 3.3 写「本聚合无状态门禁」 |
| 多状态维度 | ≥2 个状态字段 | 3.4 每维度一张图 | 只画一张 |
| 有跨聚合影响 | 动作会触发跨聚合可见状态 | 3.5 | 3.5 写「本聚合动作不跨聚合」 |
| 有外部写依赖 | 聚合内有 `requireExternal` | 6.7 | 6.7 写「本聚合无外部写依赖」 |
| 有事件订阅 | 语义 4.2 消费方非空 | 6.8 | 6.8 写「本聚合事件无消费方」 |
| 有派生属性 | 字段由计算得出 | 6.2 计算器、6.3 示例 | 工厂直接赋值 |
| 有金额 | 存在 `Money` 或金额字段 | 7.3 JSON 通道 | 7.3 删 JSON 相关行 |
| 有外部校验 | 规则需查外部数据 | 4.2 | 4.2 写「本聚合无外部校验」 |
| 有补偿动作 | 6.7 存在 | 6.4 执行器选型 | 只列 `CommandExecutor` |
| 改既有聚合 | 1.5 判定为改聚合 | 1.5、8、10 | 新建时可省 1.5 |

结构固定、数量自由：节数与子节数不变，状态维度数、子实体数、依赖数按聚合实际情况增减；不适用的子节留标题写原因，不删节、不重编号。

## 7. 质量自检

写完逐条核对，任一不通过不得进入实施。

1. §1.1 生命周期四项齐全，归档去向明确。
2. 元素级追溯表覆盖每个动作、规则、事件、状态维度、依赖。
3. 类清单与包布局数量一致。
4. 聚合根无 `@Data` / `@Builder`，无 `if + throw`。
5. 每个业务方法 `recordOperation` 先于 `collectEvent`。
6. 每个操作码都能在 §3.1 找到业务动作，且要么有事件、要么明确「无」。
7. 每条规则有消息码、激活条件、读取字段。
8. 组 C 全部读 `old`，且 §4.5 逐条列出被哪个操作改写。
9. 每个事件有载荷字段、归属操作与影响副本，载荷不含业务快照。
10. §6.4 执行器选型表覆盖全部用例，执行器与工作单元成对。
11. 一个工作单元内不出现两个操作。
12. DDL 含 `version`、四审计列、软删列，无物理外键。
13. 子表差量先 DELETE 后 INSERT；`doUpdate` 判 `affected == 0`。
14. resultMap 映射软删列，子集合懒加载字段名已核实。
15. §9 覆盖每条规则 + 组 C 不激活回归 + 夹具写法。
16. §10 六小节齐全，无空格待补，无臆造符号。
17. 全文不出现「待补」「TODO」，未定项一律进 §10.3 / §10.4。
18. 前置条件清单中与本次相关的项已确认，否则对应章节只有结构与占位。

## 8. 归档流程

1. 实施完成，代码合入。
2. 逐项核实 §10.4，把结论填进「实施回填」列。
3. 逐条比对设计与代码，把偏离填进 §10.6。
4. 在文档首部标注：`状态：已归档（YYYY-MM-DD），事实以《{Agg} 事实文档》为准`。
5. 此后不再修改；后续演进开新版本设计文档（`-v{N+1}`）或更新事实文档。

## 附录 A 空骨架

```markdown
# {Agg}聚合设计文档（写模型）

> 上游语义文档：{路径}
> 语义版本：① v{N}（定稿 YYYY-MM-DD）
> 聚合事实基线：{路径}（核对日 YYYY-MM-DD）
> 状态：草稿 / 待对齐 / 已确认 ｜ 框架版本：{版本}
> 知识来源：agent-skills/kb/

## 1. 开始
### 1.1 文档定位与生命周期
| 项 | 内容 |
| --- | --- |
| 用途 | |
| 生命周期 | 一次性文档，实施完成后归档，不再维护 |
| 归档后的事实来源 | |
| 归档前必做 | 填 §10.6，核实并回填 §10.4 |

### 1.2 聚合职责
### 1.3 与语义文档的推导关系
### 1.4 事实源边界与追溯
### 1.5 本次类型

## 2. 领域模型静态结构
### 2.1 包布局
### 2.2 类清单
| 类 | 层 | 职责 | 来源 |

### 2.3 聚合根
### 2.4 子实体
### 2.5 值对象与枚举

## 3. 状态与操作设计
### 3.1 操作清单
| 操作码 | 业务动作 | 产生事件 |

### 3.2 操作注册表
### 3.3 允许矩阵
### 3.4 状态迁移图
### 3.5 跨聚合一致性强度
| 动作 | 一致性要求 | 保障方式 |

## 4. 业务规则设计
### 4.1 规则注册表
### 4.2 外部校验契约
### 4.3 规则容器
### 4.4 覆盖对照
| # | 规则 | 消息码 | 组 | 激活条件 | 读取字段 |

### 4.5 旧快照判定的范围

## 5. 领域事件设计
### 5.1 事件清单
| 事件 | 载荷 | 构造 | 归属操作 | 影响副本 |

### 5.2 业务事件骨架
### 5.3 数据同步事件

## 6. 应用层设计
### 6.1 Input
### 6.2 工厂
### 6.3 Updater
### 6.4 写服务
### 6.5 规则装配
### 6.6 执行顺序
### 6.7 外部依赖与补偿
### 6.8 事件订阅登记

## 7. 聚合持久化（关系表）设计
### 7.1 表
### 7.2 DDL
### 7.3 映射与 TypeHandler
### 7.4 Mapper 要点
### 7.5 持久化约束

## 8. 改动清单与落地顺序
| 步 | 改动 | 依赖 | 产出 | 可编译 | 配单测 |

## 9. 测试清单
### 9.1 规则单测
| # | 场景 | 触发操作 | 期望消息码 |

### 9.2 测试夹具
### 9.3 聚合行为单测
### 9.4 场景测试
### 9.5 仓储集成测试

## 10. 与语义文档的对照、风险与回填
### 10.1 设计增量
### 10.2 待回流登记
### 10.3 业务待确认
### 10.4 框架签名
### 10.5 设计风险
### 10.6 实施回填

## 11. 附录
### 11.1 参考资料
### 11.2 与语义文档的对应
```

## 附录 B 代码模板库

**聚合根字段与业务方法**

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class {Agg} extends AggregateRoot<Long> {

    /** {业务口径}。 */
    private {Type} {field};

    /**
     * {创建语义：初始状态 + 副作用}。
     * <p>语义 {x.y} {动作名}；语义 5 R{n}。
     */
    public {Agg}({Agg}InitData data, Long {agg}Id) {
        this.setEntityId({agg}Id);
        // 字段赋值
        this.recordOperation({Agg}OperationRegistry.{OP});
        this.markCreated();
        this.markNew();
        this.collectEvent({Agg}{Action}Event.buildEvent(this));
    }

    /**
     * {意图：改哪些状态、不改哪些状态}。
     * <p>语义 {x.y} {动作名}；语义 5 R{n}。
     */
    public void {action}({Params}) {
        // 字段赋值
        this.markModified();
        this.recordOperation({Agg}OperationRegistry.{OP});
        this.collectEvent({Agg}{Action}Event.buildEvent(this));
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return {Agg}RuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return {Agg}OperationRegistry.INSTANCE;
    }
}
```

**操作注册表**

```java
public class {Agg}OperationRegistry extends OperationRegistry {

    public static final EntityOperation {OP} = EntityOperation.of("{OP}", "{描述}");

    private {Agg}OperationRegistry() {
    }

    public static final {Agg}OperationRegistry INSTANCE = new {Agg}OperationRegistry();
}
```

**规则注册表**

```java
public class {Agg}RuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode {AGG}_{RULE} =
            MessageCode.of("{AGG}_{RULE}", "{规则描述}");

    private {Agg}RuleRegistry() {
    }

    public static final {Agg}RuleRegistry INSTANCE = new {Agg}RuleRegistry();
}
```

**规则容器**

```java
public class {Agg}Rule extends EntityRule<{Agg}> {

    public {Agg}Rule({Deps}) {
        super();
        this.registerRules();
    }

    @Override
    protected boolean requireOldEntity() {
        return true;
    }

    @Override
    protected {Agg} supplyOldEntity({Agg} currentModel) {
        if (currentModel.getEntityId() == null) {
            return null;
        }
        return this.{agg}Repository.findById(currentModel.getEntityId());
    }

    private void registerRules() {
        // 组 A：总是校验
        this.addRule(EntityRule.of(model -> RuleCheckResult.of(this.{aRule}(model))),
                {Agg}RuleRegistry.{AGG}_{RULE_A});
        // 组 B：hasOperation(PLACE)
        this.addRule(EntityRule.of(model -> ...),
                {Agg}RuleRegistry.{AGG}_{RULE_B},
                IActiveRuleCondition.of(model -> model.hasOperation({Agg}OperationRegistry.PLACE)
                        ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE));
        // 组 C：hasOperation(动作) + 读 old
        this.addRule((model, old) -> RuleCheckResult.of(this.{cRule}(old)),
                {Agg}RuleRegistry.{AGG}_{RULE_C},
                IActiveRuleCondition.of(model -> model.hasOperation({Agg}OperationRegistry.{OP})
                        ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE));
    }
}
```

**事件**

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class {Agg}{Action}Event extends BaseDomainEvent {

    private {Type} {routeField};

    public {Agg}{Action}Event(String entityId) {
        super(entityId);
    }

    public static {Agg}{Action}Event buildEvent({Agg} {agg}) {
        {Agg}{Action}Event event = new {Agg}{Action}Event({agg}.getEntityId().toString());
        event.set{RouteField}({agg}.get{RouteField}());
        return event;
    }
}
```

**数据同步事件**

```java
public class {Agg}DataSyncEvent extends BaseDataSyncEvent {

    public {Agg}DataSyncEvent(String entityId, DataSyncAction dataSyncAction) {
        super(entityId, dataSyncAction);
    }

    protected {Agg}DataSyncEvent() {
        super();
    }

    public static {Agg}DataSyncEvent upsert({Agg} {agg}) {
        return new {Agg}DataSyncEvent({agg}.getEntityId().toString(), DataSyncAction.UPSERT);
    }

    public static {Agg}DataSyncEvent purge({Agg} {agg}) {
        return new {Agg}DataSyncEvent({agg}.getEntityId().toString(), DataSyncAction.PURGE);
    }
}
```

**仓储更新（含差量顺序与乐观锁）**

```java
@Override
protected void doUpdate({Agg} aggregate) {
    // 1. 子表差量：先删后插
    // 2. 主表 CAS 更新
    int affected = this.{agg}Mapper.update(aggregate);
    if (affected == 0) {
        throw new OptimisticLockingFailureException(
                "{Agg}并发更新冲突: " + aggregate.getEntityId());
    }
}
```

## 附录 C 框架机制参考

本文档只引用、不重复框架机制：

- [聚合识别](/best-practices/aggregate-identification)：事件风暴四步
- [聚合设计原则](/best-practices/aggregate-design) / [实体设计](/best-practices/entity-design) / [值对象](/best-practices/value-object)
- [业务规则（OrderRule 范式）](/best-practices/order-rule-pattern)：规则容器、激活条件与旧快照
- [应用层落地模式](/best-practices/application-collaboration)：`execute` 模板与执行顺序
- [操作注册表](/best-practices/operation-registry-design) / [事件建模](/best-practices/event-modeling)
- [聚合数据库设计](/best-practices/aggregate-database-design)：表边界、表结构规范
- [枚举值处理](/best-practices/enum-value)：出入参与投影的枚举降级
- [聚合演进设计](/best-practices/aggregate-evolution-design)：改聚合场景的三层根因与版本取舍
