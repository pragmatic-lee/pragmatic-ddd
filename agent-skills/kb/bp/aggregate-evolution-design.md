---
id: bp.aggregate-evolution-design
source: documentation/best-practices/aggregate-evolution-design.md
derived: 蒸馏自源文档，单物理副本，只读引用；改框架约定先改源文档再重派生
---

# 聚合演进设计原则（KB 卡）

> 既有聚合在迭代需求下如何演进（改聚合 / 加聚合），以及对应「修改类」设计文档怎么写。对应《聚合设计文档标准》修改场景，是 `ddd-design-doc` 产出修改类文档的依据。全新聚合走 `aggregate-identification`。

## 适用
- 给既有聚合加操作 / 命令、加子实体 / 值对象、加领域事件 / 订阅、演进式拆分。
- 不适用：全新聚合（走聚合识别）。

## 根因分析三层（修改类必写）
| 层 | 问 | 落点 |
| --- | --- | --- |
| 直接原因 | 哪段实现没拦住 | 具体代码 `行号:相对路径` |
| 深层原因 | 为什么没拦住 | 执行顺序 / 模型漏洞 |
| 本质原因 | 为什么这样设计 | **职责 / 边界 / 模型层**，非「忘了写判断」 |

## 修改场景要点
- **加操作**：操作码在 `operation-registry-design` 声明；前置类规则读**旧快照 `old`**（`execute` 先领域逻辑后校验，当前态已改）→ 见 `order-rule-pattern` + `hard-constraints`。
- **加子实体**：随父且无独立访问 → 同聚合子表（`TrackedList`+子表）；有独立访问 / 生命周期 → 提独立聚合（见 `aggregate-database-design` §2）；无身份值对象 → 并入父表 JSON。
- **加事件 / 订阅**：载荷含改前 / 改后；投影影响见 `event-subscriber-pattern` / `projection-design`；零改动也要写「零改动，理由：……」。

## 改动清单与落地顺序（模板 §11）
表格：确切文件路径 + 动作（新增 / 改 / 删）+ 说明，三列缺一不可；并给落地顺序保证每步可编译。

## 版本化
- 本次为既有聚合 `vN`；顶部 `结论先行` 标 **版本 vN** + 「替代 v(N-1) 哪些章节」。
- 文件名 `{聚合名}-aggregate-design-v{N}.md`。

## 范围控制
顺带缺口记入风险表标「不在本次范围」，避免范围蔓延。设计文档只引框架机制链接，不重复讲解。

## 反模式
| 反模式 | 纠偏 |
| --- | --- |
| 只有方案没根因 | 三层根因，本质落职责 / 边界 |
| 状态前置读当前态 | 必须读 `old` |
| 只正文描述无清单表 | 独立改动清单 + 落地顺序 |
| 期望写「报错 / 失败」 | 写确切消息码常量 / 状态枚举值 |
| 顺手一起改顺带缺陷 | 记风险表标「不在本次范围」 |

## 关联 KB
`aggregate-identification` · `aggregate-design` · `operation-registry-design` · `order-rule-pattern` · `event-subscriber-pattern` · `projection-design` · `hard-constraints` · `design-doc-template`
