# KB 重建计划（v2.0）

- [1. 背景与目标](#1-背景与目标)
- [2. 现状体检（2026-10-28）](#2-现状体检2026-10-28)
- [3. 重建范围与策略](#3-重建范围与策略)
- [4. 执行步骤](#4-执行步骤)
- [5. 卡格式规范（蒸馏纪律）](#5-卡格式规范蒸馏纪律)
- [6. 验收标准](#6-验收标准)

## 1. 背景与目标

`agent-skills/kb/` 是 pragmatic-ddd 全部 AI Skill 的**唯一共享知识源（单物理副本）**，由权威源派生（蒸馏）。当前基线 `kb_version = 1.9-draft`（2026-10-28 派生）。

此后权威源发生三类变更，`verify-kb.sh` 已报 **KB STALE（exit 1）**：

1. 7 篇 best-practices 源文档内容变更（commit `b8be623` / `c930b27`）；
2. 新增 `postgresql-config.md` 源文档，KB 无对应卡；
3. 框架版本升级 `2.0.0 → 2.1.0-SNAPSHOT`（含 PostgreSQL 方言支持，commit `9a846b8` / `8966978`），`api-contract.md` 坐标段与 `_manifest.json` 未同步。

本次目标：**将 KB 刷新到 v2.0 基线**，`verify-kb.sh` 恢复 `KB OK`，PostgreSQL 配置知识入卡，版本坐标钉死为 2.1.0-SNAPSHOT。

## 2. 现状体检（2026-10-28）

| 项 | 状态 |
| --- | --- |
| `verify-kb.sh` | **STALE，exit 1**（7 张卡源 hash 不一致） |
| STALE 卡 | bp.value-object / bp.repository-design / bp.design-doc-write-template / bp.design-doc-read-template / bp.mysql-config / bp.mybatis-config / bp.outbox-config |
| 缺卡 | `documentation/best-practices/postgresql-config.md`（commit c930b27 新增）无 `bp.postgresql-config` 卡 |
| 版本漂移 | `_manifest.json` `framework_version=2.0.0`；`api-contract.md` 坐标段全为 `2.0.0`；实际 pom 已 `2.1.0-SNAPSHOT` |
| 源码侧变更 | mybatis 模块新增 PostgreSQL 方言支持（commit 8966978），`api-contract` / `bp.mybatis-config` 需核对是否涉及新框架类 |
| 无关项 | `agents/`、`scripts/`、其余 22 张 bp 卡源未变，不需重蒸馏 |
| 顶层 README | 版本行停在 v1.7-draft，落后于 manifest（v1.9），本次一并同步 |

## 3. 重建范围与策略

**来源纪律（不变）**：KB 是产物不是源。只动 `agent-skills/kb/` 及索引/manifest，**不动** `documentation/best-practices/` 与框架源码。

| 分组 | 卡 | 源 | 动作 |
| --- | --- | --- | --- |
| A. STALE 重蒸馏 | value-object / repository-design | 对应源文档 | 重蒸馏，核对 final 方法描述修正（commit b8be623）后的语义 |
| B. STALE 重蒸馏 | mysql-config / mybatis-config / outbox-config | 对应源文档 | 重蒸馏，核对 PostgreSQL 同步后的差异（哪些配置项是 PG 特有 / 共用） |
| C. STALE 重蒸馏 | design-doc-write-template / design-doc-read-template | 对应源文档 | 重蒸馏 |
| D. 新增卡 | **bp.postgresql-config** | `documentation/best-practices/postgresql-config.md` | 新建卡，登记入 `_manifest.json`，路由给 S3（ddd-infra-config） |
| E. 版本钉死 | api-contract.md | pragmatic-ddd-core / mybatis 源码 | ① 坐标段全部 `2.0.0 → 2.1.0-SNAPSHOT`；② 核对 PostgreSQL 方言是否引入新框架类（如是，补 Tier-1/Tier-2 签名） |
| F. 同步刷新 | hard-constraints.md / projection-api.md | 多源聚合 / core 源码 | 派生时间刷新；如 A~E 蒸馏中发现约束变化，一并修正 |
| G. 索引同步 | `_manifest.json` / `kb/README.md` / `agent-skills/README.md` | — | `kb_version=2.0-draft`、`framework_version=2.1.0-SNAPSHOT`、`generated_at` 刷新、note 追加 v2.0 记录；顶层 README 版本行从 v1.7-draft 追平 |
| H. 路由微调 | `ddd-infra-config/SKILL.md` | — | S3 读卡清单加入 `bp.postgresql-config`（仅在 SKILL 显式列举配置卡时需要，先核对再改） |

**不重建**：其余 22 张 bp 卡、`scripts/`、`agents/`、其余 Skill 本体、A4-A5-walkthrough.md。

## 4. 执行步骤

1. 本计划文档（已完成，即本文件）。
2. 重蒸馏分组 A/B/C 共 7 张 STALE 卡：逐篇 Read 源文档，按 [§5](#5-卡格式规范蒸馏纪律) 重写，派生时间 2026-10-28。
3. 新建 `kb/bp/postgresql-config.md`（分组 D），头部溯源头标源文档路径。
4. `api-contract.md` 版本钉死 + PostgreSQL 方言符号核对（分组 E）：对照 mybatis/core 源码，确认方言实现类（若有）全限定名与签名。
5. `hard-constraints.md` / `projection-api.md` 派生时间刷新（分组 F）。
6. 运行 `agent-skills/scripts/derive-kb.sh` 回填全部 `source_sha`（含新卡）；再跑 `verify-kb.sh` 应 `KB OK`、exit 0。
7. 更新 `_manifest.json`：`kb_version=2.0-draft`、`framework_version=2.1.0-SNAPSHOT`、`generated_at=2026-10-28`、新增 `bp.postgresql-config` 卡条目、note 追加 v2.0 记录。
8. 更新 `kb/README.md` 与 `agent-skills/README.md` 版本行（追平至 2.0-draft，框架版本 2.1.0-SNAPSHOT）。
9. 核对并微调 `ddd-infra-config/SKILL.md` 读卡清单（分组 H，仅必要时）。
10. 输出重建报告：各卡溯源、PostgreSQL 方言核对结论、残留风险（core/mybatis 源码类卡 `source_sha` 仍为 TODO，verify 跳过，属既有设计）。

## 5. 卡格式规范（蒸馏纪律）

沿用 v1.7 建立的规范，要点不变：

1. 标题：`# BP 卡：<中文名>（<english-slug>）`。
2. 溯源头（首行引用块）：`> 源：<源文档相对路径> · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT`。
3. 剥离叙述，保留命名 / 包结构 / 骨架代码 / 关键规则 / 反模式表 / 被读于。
4. 代码块仅保留可编译骨架与最小范例（`Order` 等为范式占位）。
5. 防幻觉：框架符号一律指向 `api-contract.md` 全限定 import，禁止臆测子包。
6. 自足：不引用仓库内示例工程。
7. 章节末 `## 被读于` 标注消费方 Skill。

## 6. 验收标准

- [ ] `verify-kb.sh` 退出 0（`KB OK`），无 STALE 卡。
- [ ] `kb/bp/` 含 30 张 bp 卡（原 29 + 新增 postgresql-config），A/B/C 组 7 卡派生时间为 2026-10-28。
- [ ] `api-contract.md` 坐标段为 `2.1.0-SNAPSHOT`，PostgreSQL 方言符号核对结论留档。
- [ ] `_manifest.json`：`kb_version=2.0-draft`、`framework_version=2.1.0-SNAPSHOT`、`source_sha` 全部刷新且与源一致。
- [ ] `kb/README.md` 与 `agent-skills/README.md` 版本行与 manifest 一致。
- [ ] `documentation/best-practices/` 与框架源码零改动（`git status` 核对）。

> 本计划仅重建 KB 派生物与索引；所有卡从当前权威源重新蒸馏。**待确认后执行。**
