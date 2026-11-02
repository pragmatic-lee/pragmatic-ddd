# KB 重建计划（v2.7 轮次）

- [1. 背景与目标](#1-背景与目标)
- [2. 现状体检（2026-11-02）](#2-现状体检2026-11-02)
- [3. 重建范围与策略](#3-重建范围与策略)
- [4. 执行步骤](#4-执行步骤)
- [5. 卡格式规范（蒸馏纪律）](#5-卡格式规范蒸馏纪律)
- [6. 验收标准](#6-验收标准)
- [7. 残留风险与遗留项](#7-残留风险与遗留项)

## 1. 背景与目标

`agent-skills/kb/` 是 pragmatic-ddd 全部 AI Skill 的**唯一共享知识源（单物理副本）**，由权威源（`documentation/best-practices/` + 框架源码/pom）蒸馏派生。当前基线 `kb_version = 2.6`（`generated_at = 2026-11-01`）。

此后权威源发生一类变更，`verify-kb.sh` 已报 **KB STALE（exit 1）**：

- commit `4fcc562`（2026-11-02）修改 `documentation/best-practices/aggregate-database-design.md`（+62 / -7）：**§5.1 主键与标识**由「聚合根主键 = entityId（非自增）」单一口径，改为**两种标识落地模式**（模式一：`{agg}_id` 自增主键即 entityId，ID 后生成；模式二：`id` 物理自增主键 + `{agg}_id` 唯一索引，ID 代码生成）；**§5.3** 补两份对照建表语句；**§7** 新增「标识模式与整存整取顺序」。源侧改动已有计划文档：`docs/design/documentation/聚合数据库设计-51主键标识-重构计划.md`。

本次目标：**将 KB 刷新到 v2.7 基线** —— 标识两模式入卡、下游 Skill 建表口径同步、`verify-kb.sh` 恢复 `KB OK`。

## 2. 现状体检（2026-11-02）

| 项 | 状态 |
| --- | --- |
| `verify-kb.sh` | **STALE，exit 1**，仅 1 条：`bp.aggregate-database-design`（源 sha `eee6ebb3…` != 当前 `4b18fb9a…`） |
| 卡/源对齐 | `documentation/best-practices/` 35 篇（含 `index.md` 索引，不作卡源）↔ `kb/bp/` 35 张（含 `projection-api` 源自代码树）：**无缺卡、无孤儿卡、无 BROKEN、无 UNSEEDED、无 PRODUCT DRIFT** |
| `code-tree` 源 | `api-contract` / `bp.projection-api` 哈希一致：框架源码与 pom 自 v2.6 回填后无变更 |
| 版本口径 | pom 实际为 `2.2.0-SNAPSHOT`（`e1343aa`，2026-10-30，早于 v2.6 回填，故哈希已覆盖）；已发 Central 的正式版仍是 `2.1.0`。KB 坐标段维持 `2.1.0` 正确，但**未声明「仓库开发版 2.2.0-SNAPSHOT 不可用作消费坐标」**，存在被 AI 照抄 pom 的风险 → 本次补一行红线 |
| 下游一致性 | `ddd-infra-config/SKILL.md` §3.4 写死单一模式（「主表 `entityId` 主键…子实体表 `id` 自增」）且指向卡 §4（实际表结构规范在卡 §5）→ 与新源冲突，需修正 |
| 工作树 | `git status` 干净；`documentation/best-practices/` 与框架源码本次零改动 |

## 3. 重建范围与策略

**来源纪律（不变）**：KB 是产物不是源。只动 `agent-skills/kb/` 及其索引/manifest 与 Skill 读卡口径，**不动** `documentation/best-practices/` 与框架源码。

| 分组 | 对象 | 动作 |
| --- | --- | --- |
| A. STALE 重蒸馏 | `kb/bp/aggregate-database-design.md` | 头部派生时间改 `2026-11-02`、框架版本 `2.1.0`；§5 表结构规范改为「标识命名统一 + 两种模式 + 逻辑外键不受模式影响」，并给两模式**标识列差异**最小 SQL 片段（余列一致，不复制四份全表）；§6 一致性策略补「模式一插入须先主表拿 ID 再回填子表逻辑外键与子实体标识；模式二顺序无约束」；§7 反模式表不动（源未增行） |
| B. 防幻觉补线 | `kb/api-contract.md` 坐标段 | 追加一行：仓库开发版本 `2.2.0-SNAPSHOT` **未发布**，消费方坐标一律取已发布的 `2.1.0`，禁止由 pom 当前版本反推坐标；头部派生时间与变更说明刷新 |
| C. 读卡口径同步 | `ddd-infra-config/SKILL.md` §3.4 | 章节号 `§4` → `§5`；建表清单改为「标识模式二选一（先与用户确认：ID 后生成 / 代码生成）」，两种模式的主键/唯一索引差异与模式一的回填顺序写入自检项 |
| D. 索引同步 | `kb/README.md` | §2 表中 `aggregate-database-design` 行补「标识两模式」；底部版本行追平 v2.7 并追加变更说明 |
| E. 索引同步 | `agent-skills/README.md` | 底部版本行的 KB 版本与变更摘要追平 v2.7 |
| F. 元数据同步 | `kb/_manifest.json` | `kb_version = 2.7`、`generated_at = 2026-11-02`、`framework_version` 维持 `2.1.0`（已发布口径）、`note` 前置 v2.7 记录 |
| G. 哈希回填 | `scripts/derive-kb.sh` → `scripts/verify-kb.sh` | 先回填 `source_sha` / `product_sha`，再校验退出 0 |

**不重建**：其余 33 张 bp 卡、`hard-constraints.md`、`bp/projection-api.md`、`scripts/`、`agents/`、其余 Skill 本体、`A4-A5-walkthrough.md`。

## 4. 执行步骤

1. 本计划文档（即本文件）。
2. 重蒸馏 `kb/bp/aggregate-database-design.md`（分组 A）。
3. `kb/api-contract.md` 坐标段补开发版红线（分组 B）。
4. 修 `ddd-infra-config/SKILL.md` §3.4 建表口径与章节号（分组 C）。
5. 更新 `kb/README.md`、`agent-skills/README.md`（分组 D / E）。
6. 更新 `_manifest.json` 版本与 note（分组 F）。
7. 跑 `bash agent-skills/scripts/derive-kb.sh` 回填哈希，再跑 `bash agent-skills/scripts/verify-kb.sh`，需 `KB OK`、exit 0。
8. `git status` 核对：改动只落在 `agent-skills/`，`documentation/` 与框架源码零改动。
9. 输出重建报告（含残留风险，见 §7）。

## 5. 卡格式规范（蒸馏纪律）

沿用既有规范，要点不变：

1. 标题：`# BP 卡：<中文名>（<english-slug>）`。
2. 溯源头（首行引用块）：`> 源：<源文档相对路径> · 派生时间：2026-11-02 · 框架版本：2.1.0`。
3. 剥离叙述，保留命名 / 位置 / 骨架代码 / 关键规则 / 反模式表 / 被读于。
4. 代码块仅保留可编译骨架与最小差异范例（`Order` / `t_order` 为范式占位）。
5. 防幻觉：框架符号一律指向 `api-contract.md` 全限定 import，禁止臆测子包。
6. 自足：不引用仓库内示例工程与设计文档。
7. 只蒸馏源里有的结论；发现源内部待定点，记录到本计划 §7，不在卡里自行裁定。

## 6. 验收标准（本轮已达成）

- [x] `verify-kb.sh` 退出 0（`KB OK`），无 STALE / BROKEN / UNSEEDED / DRIFT。
- [x] `kb/bp/aggregate-database-design.md` 含标识两模式（模式一自增即 entityId + 回填；模式二物理 `id` + `{agg}_id` 唯一索引），且明确「逻辑外键不受模式影响」「两模式对写入顺序的约束不同」。
- [x] `kb/api-contract.md` 坐标段声明开发版 `2.2.0-SNAPSHOT` 不可用作消费坐标。
- [x] `ddd-infra-config/SKILL.md` §3.4 指向卡 §5，并要求先与用户确认标识模式。
- [x] `kb/README.md` 与 `agent-skills/README.md` 版本行与 `_manifest.json`（`kb_version = 2.7`）一致。
- [x] `documentation/best-practices/` 与框架源码零改动（实际变更 7 个文件全部在 `agent-skills/`）。

## 7. 残留风险与遗留项

1. **模式一的可落地性待实测**：源文档称由 MyBatis `useGeneratedKeys` + `keyProperty="entityId"` 把自增 ID 回写聚合根 `entityId`，而 `AbstractEntity` 的 `entityId` setter 由 `@Setter(AccessLevel.PROTECTED)` 生成为 **protected**（跨包反射写入是否成功取决于 MyBatis `Reflector` 的可访问性处理）。本次仅按源蒸馏入卡，**不改源、不改框架**；建议在 example 或真实工程做一次落库回填验证，若结论为「需框架开放 setter / 提供显式回填钩子」，另起 core 侧提案。
2. **产物分发落后于 KB**：形态②（Qoder 插件）与形态③（WorkBuddy 专家）产物仍停留在 KB 2.4，本次重建只刷新 `kb/` 源树，**不重打三种 zip**；需在 KB 收口后另批执行 `package-skills.sh` / `package-qoder-plugin.sh` / `package-workbuddy-expert.sh`。
3. **`先子后主` 措辞**：源 §7 与 `repository-design.md` §3.4 中该措辞指**删除/清理**顺序，而模式一插入必须**先主后子**（需拿回填 ID）。卡内按源保留两处原文并分点陈述，不做统一改写；若后续要消歧，应在源文档侧改。
