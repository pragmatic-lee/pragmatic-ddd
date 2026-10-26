---
name: ddd-design-doc
description: 聚合设计文档产出（全新需求识别 / 迭代需求改或加聚合）——先识别聚合，再按「聚合写 / 投影读」两份独立设计文档产出"版本化、结构统一"的设计。当用户说「从这份需求里识别下聚合」「帮我出聚合写 / 投影读设计文档」「这个迭代要改 / 加聚合，出个设计」「给 XX 聚合做个修改设计」时触发。本能力只产出设计文档（落 docs/design/，按迭代版本命名，一次需求产出写 / 读两份，存储设计分别归入写文档的关系表与读文档的 ES / Redis），不生成实现代码；实现交给 ddd-aggregate-create / ddd-aggregate-extend / ddd-projection-create / ddd-infra-config。
---

# ddd-design-doc（聚合设计文档产出）

> 类型：设计文档产出能力
> 版本：v2.1-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md`）
> 统一结构：`documentation/best-practices/design-doc-template.md`（两份总说明）+ 两份模板（写 / 读）

## 1. 定位

把「一份需求」转化为「两份符合标准的聚合设计文档」：聚合写（写模型 + 关系表持久化）、投影读（读模型 + ES / Redis 投影持久化）。文档分别落：

- `docs/design/{聚合名}-aggregate-design-v{N}.md`（写，**主文档，统管横切**）
- `docs/design/{聚合名}-projection-read-design-v{N}.md`（读）

**不生成实现代码**；代码交给 S4（新建）/ S5（修改）/ S6（投影）/ S3（基础设施配置）。

> **存储设计不再单独成篇**：不再产出 `docs/design/{聚合名}-persistence-design-v{N}.md`。关系表设计归入写文档 §9，ES / Redis 副本设计归入读文档 §4。

## 2. 场景模型（两类输入，一份能力，两份产出）

```text
需求 ──┬─ ① 全新需求 ──► 聚合识别(从 0) ──► 聚合设计 ──► 写 / 读 两份文档 v1
       └─ ② 迭代需求 ──┬─ 加聚合 ─► 聚合识别(识别新增) ──► 聚合设计 ──► 两份文档 v1(新聚合)
                       └─ 改聚合 ─► 聚合演进(根因+改动) ──► 两份文档 vN(含根因/改动清单)
```

- **共享阶段「聚合识别」**：事件风暴四步（见 `bp.aggregate-identification`）产出聚合候选与边界。
- **差异只在输入上下文与"新增 / 修改"**：新建类强调写 §1 业务语义、§2 静态结构、§3 状态与行为；修改类必含写 §1 根因分析、写 §8 改动清单与落地顺序。

## 3. 输入

- 业务需求文本 / 口述 / 现有代码。
- 迭代场景：既有设计文档（用于版本继承与「替代 v(N-1) 章节」标注）。

## 4. 工作流

1. **判定场景**：是否针对既有聚合？（否 → 分支 A；是 → 分支 B）
2. **聚合识别**（分支 A / B 的"加聚合"共用）：事件风暴四步，产出聚合候选清单、边界、不变量、粒度结论。
3. **并行套两份模板**：以两份模板展开——
   - **写文档**（主）：`design-doc-write-template.md` 的 12 节骨架；§8 改动清单统管整体，§9 聚合持久化（关系表）设计。
   - **读文档**：`design-doc-read-template.md` 的 7 节骨架；只聚焦读模型与投影持久化（§4），不含横切章节。
4. **版本化**：两份文件名共用 `v{N}`；结论先行块含**版本行**与「替代 v(N-1) 哪些章节」。
5. **横切统管**：改动清单 / 测试 / 风险**只在写文档**出现，且覆盖整体（写 + 读，含各自存储）；**不再单列「时序」章节**，写路径执行顺序在写文档应用层设计章节体现。
6. **自检**：两份各跑对应模板的自检清单（**写 12 / 读 7**）；任一项不满足即不符合标准。
7. **交棒**：用户确认后由 S4 / S5 / S6 / S3 落地实现。

## 5. KB 映射（只读引用）

- `bp.aggregate-identification`（聚合识别）
- `bp.aggregate-evolution-design`（聚合演进 / 修改场景）
- `bp.aggregate-design`、`bp.entity-design`、`bp.aggregate-database-design`（关系表持久化）
- `bp.projection-design`、`bp.projection-api`（读模型与投影持久化）
- `bp.elasticsearch-config`（ES 结构）
- `kb.hard-constraints`（约束 13：跨聚合只持 ID 等）
- 模板**直接读**两份（稳定模板，不走蒸馏卡，作为结构权威）：
  - `documentation/best-practices/design-doc-write-template.md`
  - `documentation/best-practices/design-doc-read-template.md`
  - 总说明与协同关系：`documentation/best-practices/design-doc-template.md`

## 6. 生成规则

- 结论先行：每份文档顶部一句话「选什么方案 + 为什么」；修改类附三层根因（直接 / 深层 / 本质）。
- 两份文档章节编号 / 标题各自恒定；不适用节保留标题并写明理由，禁止删除。
- 代码引用统一 `行号:相对路径`；枚举值出入参与投影统一用基础类型（见 `enum-value`、`hard-constraints`）。
- 前置类规则明确标注读旧快照 `old`（`execute` 先领域逻辑后校验）。
- **横切统管**：改动清单 / 测试 / 风险只在写文档，覆盖整体需求（写 + 读，含各自存储）；读文档不重复。
- **各自独立成篇**：两份不强关联、不强制交叉引用。

## 7. 防幻觉

- 边界 / 识别 / 演进判据以 KB 卡与模板为权威；拿不准先 Read 对应卡，缺符号向用户提问。
- KB 是产物不是源：不直接改卡，源约定变更先回写 `documentation/best-practices/` 再重派生。

## 8. 验收

- 两份文档各通过其对应模板自检清单（**写 12 项 / 读 7 项**）。
- 两份文档带版本元信息（文件名共用 `v{N}` + 结论先行版本行）。
- 横切章节（改动清单 / 测试 / 风险）仅在写文档出现，且覆盖整体需求。
- 存储设计归位：写文档 §9 含关系表设计、读文档 §4 含 ES / Redis 副本设计。
- 新建 / 修改类差异点与文档结构一致，评审者按固定章节即可定位。

---

> **版本**：v2.1-draft
> **关联**：`../kb/bp/aggregate-identification.md` · `../kb/bp/aggregate-evolution-design.md` · `documentation/best-practices/design-doc-template.md`（及两份模板）
