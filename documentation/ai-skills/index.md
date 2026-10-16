# Agent Skills（AI 辅助能力）

Pragmatic DDD 的 AI 编码辅助能力集合，由 Skill / 建模引导 / 设计文档产出 / 咨询 / 隔离评审角色，以及共享知识包（KB）与派生、校验脚本组成。其**唯一知识来源是本文档站的 `best-practices`**——所有 Skill 的内容都依赖 `best-practices` 产出，二者是「源 → 产物」的上下游关系。

## 架构与依赖方向

```text
documentation/best-practices/   ← 源（单一事实源，人写、进 git）
        │  scripts/derive-kb.sh（蒸馏 + 回填 source_sha）
        ▼
agent-skills/kb/                ← 蒸馏卡（单物理副本，只读引用）
        │  各 Skill 只读消费
        ▼
各 Skill / 角色                ← 产出代码或设计文档（落 docs/design/）
```

- 上游是 `best-practices`，下游是 `agent-skills/kb` 与各 Skill；**Skills 不反向改源**，源约定变更先回写 `best-practices` 再重派生。
- KB 卡正文由 AI + 人审蒸馏（剥离叙述，保留命名 / 位置 / 骨架 / 关键规则 / 反模式），`verify-kb.sh` 在源哈希变化时标 STALE 并阻断合并（CI 卡口）。

## 能力总览

| 入口 | 类型 | 触发意图 |
| --- | --- | --- |
| `ddd-project-init` | 初始化 | 新建 DDD 工程 |
| `ddd-aggregate-scaffold` | 骨架 | 建聚合目录骨架 |
| `ddd-infra-config` | 基础设施 | 配 MySQL/MQ/Outbox/ES/Redis |
| `ddd-aggregate-create` | 创建（核心） | 生成完整聚合 |
| `ddd-aggregate-extend` | 扩展 | 给聚合加操作/校验/订阅/子实体 |
| `ddd-projection-create` | 投影 | 生成查询侧读模型 |
| `ddd-code-review` | 评审 | 评审代码合规 |
| `ddd-modeling-guide` | 建模引导 | 聚合边界/聚合度怎么划（只引导，不出文档） |
| `ddd-design-doc` | 设计文档产出 | 从需求识别聚合并出标准/版本化设计文档（全新/加聚合/改聚合） |
| `ddd-kb-consult` | 咨询答疑 | 解释规范/最佳实践（不产代码） |
| `agents/ddd-reviewer` | 隔离评审角色 | 与 S7 同基准，只读、零写入 |

各 Skill 的 KB 映射与详细用法见随仓库托管于根级 `agent-skills/` 的 `README.md`。

## 设计文档产出（ddd-design-doc）

`ddd-design-doc` 是「需求 → 设计文档」的能力，覆盖两类输入、一种能力：

- **全新需求**：聚合识别（事件风暴四步）→ 聚合设计 → 设计文档 `v1`。
- **迭代需求**：
  - 加聚合 → 聚合识别（识别新增）→ 设计文档 `v1`（新聚合）。
  - 改聚合 → 聚合演进（根因 + 改动）→ 设计文档 `vN`（含 §1.3 根因、§11 改动清单）。

文档统一遵循《[聚合设计文档标准](./best-practices/design-doc-template)》的 13 节固定骨架，并带版本元信息（`{聚合名}-aggregate-design-v{N}.md`），实现跨迭代可比对、可评审。

## KB 使用纪律

1. **KB 是产物不是源**：卡从 `best-practices` 派生，绝不反向改卡。
2. **单物理副本、只读引用**：Skill 只声明「读哪几篇」，不各带一份拷贝。
3. **fail-fast**：Skill 找不到对应 KB 时停止生成并报告，禁止凭记忆续写。
4. **溯源 + 漂移治理**：`_manifest.json` 记真实 `source_sha`；`verify-kb.sh` 在源哈希变化时标 STALE（CI 卡口）；`derive-kb.sh` 半自动回填 sha256。

## 相关文档

- [聚合识别设计原则](./best-practices/aggregate-identification)
- [聚合演进设计原则](./best-practices/aggregate-evolution-design)
- [聚合设计文档标准](./best-practices/design-doc-template)
- [最佳实践总览](./best-practices/)
