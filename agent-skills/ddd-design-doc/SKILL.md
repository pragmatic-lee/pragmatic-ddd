---
name: ddd-design-doc
description: 聚合设计文档产出（全新需求识别 / 迭代需求改或加聚合）——先核对项目前置条件，再按「业务语义 → 写设计 → 投影读设计」三层产出文档。当用户说「从这份需求里识别下聚合」「梳理一下这个聚合的业务语义」「帮我出聚合写 / 投影读设计文档」「这个迭代要改 / 加聚合，出个设计」时触发。本能力只产出文档，不生成实现代码；实现交给 ddd-aggregate-create / ddd-aggregate-extend / ddd-projection-create / ddd-infra-config。
---

# ddd-design-doc（聚合设计文档产出）

> 类型：设计文档产出能力
> 版本：v3.0 · 适用框架：2.1.0-SNAPSHOT
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md`）
> 统一结构：`documentation/best-practices/design-doc-template.md`（三层总说明）+ 三份模板

## 1. 定位

把「一份需求」转化为「三层文档」，每层独立交付、独立评审：

| 层 | 产出 | 落盘 | 评审人 |
| --- | --- | --- | --- |
| ① 业务语义 | 属性 / 动作 / 事件 / 规则 / 状态 / 依赖 / 查询场景 | `docs/design/documentation/{agg}-business-semantics.md` | 业务方 + 产品 |
| ② 聚合写 | 写模型 + 关系表持久化 | `docs/design/{agg}-aggregate-design-v{N}.md` | 开发 |
| ③ 投影读 | 读模型 + 读源结构（ES / Redis / 数据库直读） | `docs/design/{agg}-projection-read-design-v{N}.md` | 开发 |

- ② 为主文档，统管横切（改动清单 / 测试 / 风险 / 实施回填）；③ 不设这三章。
- ② 与 ③ 为**一次性文档**，实施完成后归档；① 随需求演进。
- **不生成实现代码**；代码交给 S4 / S5 / S6 / S3。

## 2. 场景模型

```text
需求 ──┬─ ① 全新需求 ──► 聚合识别 → 语义 → 确认 → 写 / 读设计
       └─ ② 迭代需求 ──┬─ 加聚合 ─► 聚合识别（新增）→ 同上
                        └─ 改聚合 ─► 聚合演进（先改语义）→ 写 / 读设计 vN
```

关键约束：**语义文档必须人工确认后才能进入设计阶段**。未经确认的语义视为草稿。

## 3. 输入

- 业务需求文本 / 口述 / 现有代码。
- **项目前置条件清单**（必读，见 `bp.design-doc-prerequisites`）；清单不存在时先协助建立。
- 迭代场景：既有三份文档（用于版本继承与「替代 v(N-1) 章节」标注）。

## 4. 工作流（10 步）

| 步 | 动作 | 产出 / 判定 |
| --- | --- | --- |
| 0 | **核对前置条件** | 逐项比对阻断映射；未确认项记录并在后续步骤中只写结构占位 |
| 1 | 判定场景 | 全新 / 加聚合 / 改聚合 |
| 2 | 聚合识别 | 事件风暴四步：聚合候选、边界、不变量、粒度 |
| 3 | **产出语义文档** | 8 章 + 3 附录，含查询场景 Q 编号 |
| 4 | **人工确认语义** | 未确认不得进入第 5 步；记录确认人 |
| 5 | 产出写设计 | 11 节；§1.4 建元素级追溯；§10 六类待定项 |
| 6 | 产出投影读设计 | 6 节；§1.1 承 Q 编号；§1.3 读源与澄清 |
| 7 | 三份自检 + 交叉核对 | 追溯覆盖、Q 编号对齐、读源与查询族对应 |
| 8 | 交棒实施 | 用户确认后由 S4 / S5 / S6 / S3 落地 |
| 9 | 回填与归档 | 填写文档 §10.6、核实 §10.4；写与读一起归档 |

## 5. KB 映射（只读引用）

**本能力专属**

- `bp.design-doc-semantics`（语义文档 8 章 + 3 附录）
- `bp.design-doc-write-template`（写设计 11 节）
- `bp.design-doc-read-template`（投影读 6 节 + 三读源）
- `bp.design-doc-prerequisites`（前置条件 10 项 + 阻断映射）

**场景相关**

- `bp.aggregate-identification`（聚合识别）
- `bp.aggregate-evolution-design`（改聚合：三层根因与版本取舍）
- `bp.aggregate-design` / `bp.entity-design` / `bp.aggregate-database-design`（写模型与关系表）
- `bp.projection-design` / `bp.projection-api`（读模型与投影）
- `kb.hard-constraints`（跨聚合只持 ID 等）

**结构权威**：三份模板直接读 `documentation/best-practices/design-doc-*.md`，不走蒸馏卡（模板属稳定结构，卡为索引）。

## 6. 生成规则

- **只写设计，不写论证**：产出表 / 骨架 / DDL / mapping / 清单，不写取舍与推导。
- **单一事实源**：业务事实只在语义文档定义；设计文档引用编号，不重抄。
- **编号稳定**：规则 R、查询 Q 跨文档不变，分配后不重排。
- **条件子节不删、不重编号**：不适用时保留标题写「本聚合不适用：{原因}」，保证跳章引用稳定。
- **未定项集中**：框架签名、业务待确认、实施偏差只在写文档 §10；语义与读文档只引用。
- **前置条件阻断**：未确认项对应的章节只写结构与占位，不写结论。
- **不设接口层章节**：端点与 Request / Response DTO 不在范围。

## 7. 防幻觉

- 框架符号以 `../kb/api-contract.md` 为唯一权威；未登记的一律进写文档 §10.4，不臆造签名。
- 边界 / 识别 / 演进判据以 KB 卡为权威；拿不准先 Read 对应卡。
- KB 是产物不是源：源约定变更先回写 `documentation/best-practices/` 再重派生。

## 8. 验收（8 条）

1. 三份文档齐全，版本号一致（语义文档标依据来源）。
2. **语义文档已人工确认**，有确认记录。
3. **元素级追溯覆盖**全部动作、规则、事件、状态维度、依赖。
4. **前置条件清单中相关项已确认**；未确认项对应章节只有结构与占位。
5. 写文档自检全通过（含组 C 读 old、差量顺序、执行器选型）。
6. 读文档自检全通过（含读源澄清、Q 编号对齐、直读有索引）。
7. 横切章节只在写文档出现，覆盖写 + 读。
8. **归档前 §10.4 与 §10.6 回填完成**。

## 9. 与下游 skill 的关系

| 下游 | 消费什么 |
| --- | --- |
| `ddd-aggregate-create` | 写文档 §2–§9 |
| `ddd-aggregate-extend` | 写文档 §1.5、§8、§10.1 |
| `ddd-projection-create` | 读文档 §2–§6 |
| `ddd-infra-config` | 写文档 §7 + 前置条件清单 |

---

> **版本**：v3.0（三层文档 + 前置条件闸门 + 人工确认）
> **关联**：`../kb/bp/design-doc-semantics.md` · `design-doc-write-template.md` · `design-doc-read-template.md` · `design-doc-prerequisites.md` · `documentation/best-practices/design-doc-template.md`
