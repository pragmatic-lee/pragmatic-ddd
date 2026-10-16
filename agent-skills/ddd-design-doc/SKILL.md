---
name: ddd-design-doc
description: 聚合设计文档产出（全新需求识别 / 迭代需求改或加聚合）——先识别聚合，再按《聚合设计文档标准》13 节骨架产出"版本化、结构统一"的设计文档。当用户说「从这份需求里识别下聚合」「帮我出一份聚合设计文档」「这个迭代要改 / 加聚合，出个设计」「给 XX 聚合做个修改设计」时触发。本能力只产出设计文档（落 docs/design/，按迭代版本命名），不生成实现代码；实现交给 ddd-aggregate-create / ddd-aggregate-extend。
---

# ddd-design-doc（聚合设计文档产出）

> 类型：设计文档产出能力
> 版本：v1.0-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md`）
> 统一结构：`documentation/best-practices/design-doc-template.md`（13 节固定骨架）

## 1. 定位

把「一份需求」转化为「一份符合标准的聚合设计文档」。文档落 `docs/design/{聚合名}-aggregate-design-v{N}.md`，**不生成实现代码**；代码交给 S4（新建）/ S5（修改）。

## 2. 场景模型（两类输入，一种能力）

```text
需求 ──┬─ ① 全新需求 ──► 聚合识别(从 0) ──► 聚合设计 ──► 设计文档 v1
       └─ ② 迭代需求 ──┬─ 加聚合 ─► 聚合识别(识别新增) ─► 聚合设计 ──► 文档 v1(新聚合)
                       └─ 改聚合 ─► 聚合演进(根因+改动) ─► 文档 vN(含根因/改动清单)
```

- **共享阶段「聚合识别」**：事件风暴四步（见 `bp.aggregate-identification`）产出聚合候选与边界。
- **差异只在输入上下文与"新增 / 修改"**：新建类强调 §2 边界、§3 静态结构、§4 状态机；修改类必含 §1.3 根因分析、§11 改动清单与落地顺序。

## 3. 输入

- 业务需求文本 / 口述 / 现有代码。
- 迭代场景：既有设计文档（用于版本继承与「替代 v(N-1) 章节」标注）。

## 4. 工作流

1. **判定场景**：是否针对既有聚合？（否 → 分支 A；是 → 分支 B）
2. **聚合识别**（分支 A / B 的"加聚合"共用）：事件风暴四步，产出聚合候选清单、边界、不变量、粒度结论。
3. **套模板**：以 `design-doc-template.md` 的 13 节骨架展开；不适用节保留标题写「零改动，理由：……」。
4. **版本化**：文件名 `{聚合名}-aggregate-design-v{N}.md`；`结论先行` 块含**版本行**与「替代 v(N-1) 哪些章节」。
5. **自检**：跑 `design-doc-template.md` §5 的 13 项符合性自检；任一项不满足即不符合标准。
6. **交棒**：用户确认后由 S4 / S5 落地实现。

## 5. KB 映射（只读引用）

- `bp.aggregate-identification`（聚合识别）
- `bp.aggregate-evolution-design`（聚合演进 / 修改场景）
- `bp.aggregate-design`、`bp.entity-design`、`bp.aggregate-database-design`
- `kb.hard-constraints`（约束 13：跨聚合只持 ID；旧快照机制等）
- 模板**直接读** `documentation/best-practices/design-doc-template.md`（稳定模板，不走蒸馏卡）

## 6. 生成规则

- 结论先行：一句话「选什么方案 + 为什么」；修改类附三层根因（直接 / 深层 / 本质）。
- 13 节编号标题恒定；不适用节保留标题并写明理由，禁止删除（避免被误判遗漏）。
- 代码引用统一 `行号:相对路径`；枚举值出入参与投影统一用基础类型（见 `hard-constraints` 与 `enum-value`）。
- 前置类规则明确标注读旧快照 `old`（`execute` 先领域逻辑后校验）。

## 7. 防幻觉

- 边界 / 识别 / 演进判据以 KB 卡与模板为权威；拿不准先 Read 对应卡，缺符号向用户提问。
- KB 是产物不是源：不直接改卡，源约定变更先回写 `documentation/best-practices/` 再重派生。

## 8. 验收

- 产出文档通过 `design-doc-template.md` §5 的 13 项自检。
- 文档带版本元信息（文件名 v{N} + 结论先行版本行）。
- 新建 / 修改类差异点与文档结构一致，评审者按固定章节即可定位。

---

> **版本**：v1.0-draft
> **关联**：`../kb/bp/aggregate-identification.md` · `../kb/bp/aggregate-evolution-design.md` · `documentation/best-practices/design-doc-template.md`
