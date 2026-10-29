# 投影读设计文档模板（读模型）

> 落地路径：`docs/design/{聚合名}-projection-read-design-v{N}.md`。
> 上游：语义文档 §8（查询场景）→ 本模板；写侧设计见 `design-doc-write-template`。
> 本模板产出**设计产物**：只给结构、mapping、类、SQL、查询编排，不写选型理由与推导。
> 与写设计文档**同批归档**，最终事实由「聚合事实文档」承接。

## 1. 这份文档是什么

### 1.1 定位

与「聚合写设计文档」成对的读侧专项文档。写文档管「数据怎么变」，本文档管「数据怎么查」。

| 文档 | 回答的问题 | 节数 |
| --- | --- | --- |
| 聚合写设计文档（主文档） | 聚合、规则、事件、表、应用层怎么写 | 11 |
| **聚合投影读设计文档（本文档）** | 副本有哪些、索引长什么样、查询怎么走 | 6 |
| 聚合事实文档 | 当前系统实际是什么 | — |

### 1.2 生命周期

| 项 | 约定 |
| --- | --- |
| 用途 | 本次需求的读模型实施依据 |
| 归档时点 | 与写设计文档**同批归档** |
| 归档后事实来源 | 由「聚合事实文档」承接 |
| 归档前必做 | 与写文档共用的 §10.6 回填栏一并完成 |

### 1.3 与写设计文档的分工

| 内容 | 归属 |
| --- | --- |
| 表结构、聚合、规则、领域事件、写服务 | 写设计文档 |
| 业务订阅（短信、积分、通知等） | 写设计文档 |
| 副本、投影 DTO、条件族、源、裁剪器、读服务 | 本文档 |
| **投影侧订阅接线与物化实现** | 本文档 |
| 改动清单、测试、风险、框架签名待核实 | 写设计文档（本文档不设这三章） |
| 接口层（HTTP / RPC / Response DTO） | 两者都不写 |

**唯一事实源**：同一件事只在一处定义。本文档只写读侧形态，写侧一律指向写文档。

### 1.4 不写什么

- 不写存储选型理由（ES 还是 Redis，只给结论）。
- 不写改动清单、测试、风险——由写文档统管。
- 不写接口层：端点、Request / Response DTO 不在范围。
- 不写演进根因与版本取舍。
- 不写读图说明。

## 2. 输入源

### 2.1 输入一：写设计文档

| 从这里取 | 用途 |
| --- | --- |
| 表结构与字段 | 决定投影 DTO 字段 |
| 领域事件与同步事件 | 决定订阅与同步路径 |
| 订阅别名（如 `es` / `redis-cache` / `reconcile`） | 与写文档对齐，不另起名字 |
| 聚合的版本语义 | 决定副本 `version` 与对账判定 |

### 2.2 输入二：语义文档 §8 查询场景

直接承接语义文档 §8 的 Q 编号，逐条翻译成查询方式与数据来源，**不新增编号、不改需求含义**：

| # | 需求（承语义 §8） | 查询方式 | 数据来源 |
| --- | --- | --- | --- |

规则：

- 语义 §8 缺某条时，先回语义文档补齐，不在本文档凭空新增。
- 一条需求可以对应多个查询族（如导出走 Page + Scroll），但仍只用一个 Q 编号。
- 展示裁剪（摘要 / 详情）不是独立场景，写入 §6.2 的裁剪目标列，不占 Q 编号。

### 2.3 输入三：读源澄清结论

读源由业务方澄清后落表。三种形态不同，不能混写：

| 读源 | 需要写副本 | 需要定义 | 是否走四角色 | 是否有同步事件 |
| --- | --- | --- | --- | --- |
| Elasticsearch | 是 | mapping、索引名、REPLICA_ID、版本策略 | 是 | 是 |
| Redis | 是 | 投影 Java 类、key 规则、TTL、version | 是（仅 ById） | 是 |
| 数据库同源表 | 否 | 查询 SQL / Mapper、索引、返回 DTO | 否 | 否 |

规则：

- 每个 Q 必须落到一个读源，并在 §1.3 标注澄清状态：已确认 / 未澄清。
- **未澄清的 Q 不得默认选型**，登记为待办，对应查询暂不设计；文档不因待办而完整。
- 选型只给结论，不写「为什么选 ES」。
- 只有一种读源时，其余读源子节保留标题写「本聚合不适用」。

## 3. 固定目录

6 节，子节与顺序固定；标注「条件」的子节不适用时保留标题、不重编号，正文写「本副本不适用：{原因}」。

```
1. 概述与背景
   1.1 查询需求（承语义 §8）
   1.2 来源事件
   1.3 读源与澄清
2. 投影整体设计
   2.1 四角色与直读的差异
   2.2 副本维度
   2.3 领域契约
3. 读模型静态结构（逻辑模型）
   3.1 投影 DTO（含直读 DTO）
   3.2 条件族（含直读参数）
4. 读源结构设计（物理结构）
   4.1 ES 结构（条件，含副本常量）
   4.2 Redis 结构（条件，含副本常量）
   4.3 数据库直读（条件）
   4.4 副本版本语义
5. 投影构建与同步路径（仅异构副本）
   5.1 投影器
   5.2 裁剪器（条件）
   5.3 源
   5.4 订阅与路径
6. 查询设计
   6.1 读服务
   6.2 查询族与源对应
   6.3 分页约定（条件）
   6.4 ES 条件工厂（条件）
   6.5 读路径的一致性边界
```

## 4. 逐节规约

### 4.1 §1 概述与背景

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 1.1 查询需求 | 编号 / 需求 / 查询方式 / 数据来源 | 4 列表 |
| 1.2 来源事件 | 事件 / 同步动作 / 触发时机 | 3 列表 |
| 1.3 读源与澄清 | 读源 / 需要写副本 / 承载查询 / 副本标识 / 澄清状态；另附待澄清表 | 2 张表 |

**硬规则**

- 查询需求必须编号（Q1…Qn），§6.2 要逐条对应。
- 来源事件只能是数据同步事件，且必须说明「只带标识 + 动作」。
- 读源必须标注澄清状态；未澄清的进待澄清表，不进入后续设计。

**反例**：写一段「考虑到订单查询需要多条件组合，因此选择 ES」。

### 4.2 §2 投影整体设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 2.1 四角色 | 角色 / 本聚合实现 / 职责 | 3 列表，固定 4 行 |
| 2.2 副本维度 | 副本 / REPLICA_ID / 全量投影 / 裁剪目标 / 支撑查询族 | 5 列表 |
| 2.3 领域契约 | 投影根接口 + 每个源端口 | 完整代码 |

**硬规则**

- 四角色固定：投影器、源（自身即副本）、裁剪器、读服务，不多不少；数据库直读不使用四角色。
- 副本标识格式固定 `{store}:{agg}`，每个副本独立、不共用。
- 源端口只 `extends` 真实支撑的查询族：Redis 只 ById，ES 按需取 One / List / Page。
- `getReducer` 声明在源端口上，不在读服务里自己找。

**反例**：源端口 extends 四族但实现只支持 ById。

### 4.3 §3 读模型静态结构

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 3.1 投影 DTO | 投影表（用途 + 关键字段）+ 1 个全量投影完整示例；直读 DTO 也列在本表 | 表 + 代码 |
| 3.2 条件族 | 每个被支撑的查询族一个 record；数据库直读参数另设普通 record | 代码 |

**硬规则**

- **枚举一律降级**：`Integer` code + `String` name 双写，投影里不出现枚举类型。
- **金额原样**：`BigDecimal` 直接承载，单位与精度与聚合一致，换算只发生在「聚合 → 全量投影」一次。
- 投影 DTO 用 `@Data`（读模型允许，与聚合根的 Lombok 约定不同）。
- 缓存副本必须自带 `version` 字段；ES 副本用外部版本控制，不依赖 `_version`。
- 条件族三族互不继承；One / List 字段必填，Page 字段全 `Optional`；条件不含分页参数。
- **只给源端口实际 extends 的族写条件**：不支撑的族不写 record；数据库直读参数不实现任何 criteria 接口，也不占查询族名额。
- **副本常量跟随存储放在 §4**：每个读源一个小节内定义 `REPLICA_ID` 等常量，不在 §3 集中堆。
- 投影根接口默认用普通 `interface`；若要封闭体系改 `sealed`，实现类必须声明 `final` / `sealed` / `non-sealed`。

**反例**：投影 DTO 里出现 `OrderStatus status`；条件族里塞 `pageNumber`。

### 4.4 §4 读源结构设计

本章原名「投影持久化设计」，加入数据库直读后改名：直读不是持久化，但确实是「一种读源的结构」。

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 4.1 ES 结构（条件） | 索引名 / 别名 / REPLICA_ID / 分片 / 主键 / 版本策略 + 副本常量 + mapping | 表 + 代码 + JSON |
| 4.2 Redis 结构（条件） | 键 / 值 / 版本字段 / TTL / 支撑族 + 副本常量 | 表 + 代码 |
| 4.3 数据库直读（条件） | 表 / 谓词 / 索引 + SQL + 一致性；DTO 回指 §3.1 | 表 + XML |
| 4.4 副本版本语义 | 场景 / 返回值 / 判定（仅异构副本） | 3 列表 |

有几种读源就写几个结构子节，编号按顺序自行顺延；不存在的读源保留一个小节写「本聚合不适用」。

**硬规则**

- §4 只给结构产物：mapping、键结构、常量、版本规则。不写建索引的理由。
- ES 索引名带版本号（`{agg}_index_v1`），对外用别名，重建时切别名。
- 金额字段用 `scaled_float` 且 `scaling_factor` 与精度一致（两位小数用 100）。
- nested 结构用于子实体集合（如明细），普通字段不用 nested。
- Redis 版本：`readVersion` 副本缺失返回 `0`（判 STALE → rebuild），**不返回 `-1`**；`-1` 只表示写模型不存在（ORPHAN）。
- §4 与 §6 的查询族必须对得上：ES 支撑的族要能在 mapping 里找到对应字段。
- **数据库直读**不写副本、不发同步事件，查询 DTO 不实现投影根接口，也不走投影器与裁剪器。
- 直读 SQL 里不写死展示文案：JSON 列用 `JSON_EXTRACT` 抽数值，状态名由读服务用枚举回填。
- 直读的过滤列必须在写侧 DDL 有对应列或生成列与索引；没有就回写设计文档补，不在读侧另建冗余列。

**反例**：mapping 里没有 `customerId`，但条件族按客户检索。

### 4.5 §5 投影构建与同步路径

**本节仅适用于异构副本**（ES / Redis）。数据库同源表直读没有构建与同步过程，只写 §4.3。

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 5.1 投影器 | 1 个完整示例 | 完整代码 |
| 5.2 裁剪器 | 1 个完整示例 + 一句「其余同构」 | 完整代码 |
| 5.3 源 | 构造 + 四个覆写方法 | 骨架代码 |
| 5.4 订阅与路径 | 订阅表 + 1 个订阅者示例 + 三条路径表 | 表 + 代码 |

**硬规则**

- 投影器纯映射：无存储访问、无版本控制、无业务计算（派生字段除外）。
- 裁剪器是纯函数：`reduce` 内不查库、不调远程；`source == null` 返回 `null`；一个子投影一个裁剪器。
- 源构造五参顺序固定：`ProjectionSource` → `aggregateType` → `projectionType` → `projector` → `reducers`。
- `materialize` 第一行强转本源投影类型。
- 订阅者**先按 `getDataSyncAction()` 分流**：`PURGE` → `purge(id)`；`UPSERT` → 回查聚合 → `sync(aggregate)`；聚合为 `null` 转 `purge(id)`，不静默返回。
- 三条路径必须都写：写路径、删除路径、对账路径。
- `rebuild` 从写模型当前快照重建（`findById` → `sync`），不重放单条事件。
- **投影侧的物化实现与订阅接线必须在本文档**：`materialize` / `purge` / `readVersion` / `rebuild` 写到可照抄，投影订阅单独一个配置类登记；写文档只保留业务订阅。

**反例**：`handleEvent` 直接 `sync`，不判动作；聚合查不到就 `return`。

### 4.6 §6 查询设计

| 子节 | 固定内容 | 形式 |
| --- | --- | --- |
| 6.1 读服务 | 1–2 个完整方法 | 代码 |
| 6.2 查询族与源对应 | 方法 / 查询族 / 源 / 全量投影 / 裁剪目标 | 5 列表 |
| 6.3 分页约定 | 入参 / 分页位置 / totalCount / 游标 | 4 行表 |
| 6.4 ES 条件工厂 | 位置 / 形态 / 职责 / 枚举与金额处理 | 表 |
| 6.5 读路径的一致性边界 | 场景 / 处理 | 表 |

**硬规则**

- 读服务 `implements IQueryApplicationService`，**不继承任何查询基类**。
- 选源写死在方法体内，不把 `ProjectionSource` 或源对象当入参暴露。
- **两跳**：查全量 → `getReducer(目标类型).reduce(...)`；目标即全量时直接返回。
- `getReducer` 返回 `null` 时抛 `ProjectionReducerNotFoundException`，不当「查不到」处理。
- `totalCount` / `nextCursor` 取裁剪前，裁剪后不重算。
- 不回源聚合根；Redis 未命中返回 `null`，不自动回退 ES。
- 条件 → DSL 的转换下沉到条件工厂（`public final` 纯函数），读服务里不拼 DSL。

**反例**：读服务里注入 ES Client 拼查询；裁剪后重新统计总数。

## 5. 代码形式规约

### 5.1 每类对象给多少代码

| 对象 | 代码量 | 其余怎么表达 |
| --- | --- | --- |
| 投影根接口 | 完整（很短） | — |
| 源端口 | 完整（很短） | — |
| 投影 DTO | **1 个全量投影完整示例** | 投影表（用途 + 关键字段） |
| 条件族 | 每族 1 个 record | — |
| 副本常量 | 完整 | 随存储放在 §4 对应小节 |
| 投影器 | **1 个完整示例** | 另一存储「同构」一句 |
| 裁剪器 | **1 个完整示例** | 「其余同构」一句 |
| 源 | 构造 + 四个覆写 | — |
| 订阅者 | **1 个完整示例** | 订阅表 |
| 读服务 | **1–2 个方法** | 查询族对应表 |
| ES mapping | 完整 JSON | — |
| Redis 结构 | 表 | — |
| 直读 SQL | 完整 XML | — |

判定法同写文档：不写实施者会写错，就写；只是懒得打，就不写。

### 5.2 骨架写法约定

| 对象 | 固定写法 |
| --- | --- |
| 投影 DTO | `@Data` + 字段 Javadoc（口径一句话）+ 实现投影根接口 |
| 条件族 | `record` + `implements {One/List/Page}QueryCriteria`；数据库直读参数为普通 `record` |
| 副本常量 | `public final class` + 私有构造 + `REPLICA_ID` |
| 投影器 | `extends AbstractAggregateProjector<{Agg}, P>` + 构造传 `P.class` |
| 裁剪器 | `implements IReducer<S, X>` + `projectionType()` + `reduce` 判空 |
| 源 | `extends AbstractProjectionSource<{Agg}, ID, P>` + `implements I{Agg}{Store}Source` |
| 订阅者 | `implements I{Agg}{事件}Handle` + `handleEvent` 先判动作 |
| 读服务 | `implements IQueryApplicationService` + 构造注入源端口 |

### 5.3 命名

| 元素 | 命名 | 示例 |
| --- | --- | --- |
| 投影根接口 | `I{Agg}Projection` | `IOrderProjection` |
| 源端口 | `I{Agg}{Store}Source` | `IOrderEsSource` |
| 源实现 | `{Agg}{Store}Source` | `OrderEsSource` |
| 投影 DTO | `{Agg}{Store/semantic}Projection` | `OrderEsProjection` |
| 条件族 | `{Agg}{One/List/Page}Query` | `OrderPageQuery` |
| 裁剪器 | `{Agg}{Target}Reducer` | `OrderSummaryReducer` |
| 副本常量 | `{Agg}{Store}Targets` | `OrderEsTargets` |
| 订阅别名 | kebab-case `{动作}-on-{事件}` | 与写文档一致 |

## 6. 副本特征矩阵

| 特征 | 判定信号 | 影响的子节 | 无此特征时的写法 |
| --- | --- | --- | --- |
| 有 ES 副本 | 需要条件检索 / 分页 | 4.1、5.3、6.3 | 4.1 写「本聚合无 ES 副本」 |
| 有 Redis 副本 | 需要按标识高频读取 | 4.2、5.3 | 4.2 写「本聚合无缓存副本」 |
| 有子投影裁剪 | 列表 / 详情字段少于全量 | 3.1 子投影、5.2 裁剪器、6.2 裁剪目标 | 3.1 只留全量投影；5.2 写「无裁剪」 |
| 有分页 | 需求含列表翻页 | 6.3 | 6.3 写「本聚合无分页场景」 |
| 有跨副本对账 | 副本 ≥ 2 | 2.2 副本维度、5.4 对账路径 | 5.4 只写写路径与删除路径 |
| 有数据库直读 | 部分查询直接查同源表 | 3.1 直读 DTO、4.3、6.2 对应行 | 4.3 写「本聚合无数据库直读」 |
| 多状态维度 | 聚合有 ≥2 个状态字段 | 3.1 状态字段双写、4.1 多状态索引字段 | 保留单状态 |

新增副本的固定动作（缺一不可）：新源端口 + 新源实现 + 新订阅者接线 + 新 `REPLICA_ID`。

## 7. 质量自检

1. 6 节齐全，没有改动清单 / 测试 / 风险 / 接口层章节。
2. §1.1 的 Q 编号与语义 §8 逐条对齐，无增无减。
3. 四角色与领域契约齐全，副本标识格式为 `{store}:{agg}`。
4. 投影里无枚举类型，全部 `Integer` code + `String` name。
5. 金额字段 `BigDecimal` 原样，换算只发生一次。
6. §4 只给结构产物，无论证性散文。
7. ES 有索引名、别名、`REPLICA_ID`、分片、主键、版本策略。
8. Redis 有键结构、TTL、`version` 字段、支撑族。
9. 源端口只 extends 真实支撑的族；Redis 不含 List / Page。
10. 读服务两跳、显式选源、不回源、`totalCount` 取裁剪前。
11. `getReducer` 返回 null 时抛 `ProjectionReducerNotFoundException`。
12. 订阅者按 `getDataSyncAction()` 分流，聚合缺失转 `purge`。
13. `readVersion` 副本缺失返回 `0`，不是 `-1`。
14. 新增副本三步接线齐全（源 + 端口 + 订阅）。
15. 全文无「待补」「TODO」；未定项指向写文档的 §10。
16. 每个 Q 都标注了读源与澄清状态，未澄清的不进入设计。
17. 数据库直读的过滤列在写侧 DDL 有索引；JSON 抽取与枚举回填不在 SQL 里写死。

## 8. 归档流程

与写设计文档**同批归档**：

1. 读写两侧实施完成。
2. 写文档的 §10.4 / §10.6 回填完毕。
3. 本文档核对：副本、索引、查询族与实现一致。
4. 首部标注：`状态：已归档（YYYY-MM-DD），事实以《{Agg} 事实文档》为准`。
5. 此后不修改；新增副本视为新需求，开新版本。

## 附录 A 空骨架

```markdown
# {Agg}聚合投影读设计文档（读模型）

> 上游写设计文档：{路径}
> 上游语义文档：{路径}
> 框架版本：{版本}

## 1. 概述与背景
### 1.1 查询需求
| # | 需求（承语义 §8） | 查询方式 | 数据来源 |

### 1.2 来源事件
| 事件 | 同步动作 | 触发 |

### 1.3 读源与澄清
| 读源 | 需要写副本 | 承载查询 | 副本标识 | 澄清状态 |

| # | 待澄清项 | 候选读源 | 结论 |

## 2. 投影整体设计
### 2.1 四角色与直读的差异
| 角色 | 本聚合实现 | 职责 |

### 2.2 副本维度
| 副本 | REPLICA_ID | 全量投影 | 裁剪目标 | 支撑查询族 |

### 2.3 领域契约

## 3. 读模型静态结构
### 3.1 投影 DTO（含直读 DTO）
### 3.2 条件族（含直读参数）

## 4. 读源结构设计
### 4.1 ES 结构
### 4.2 Redis 结构
### 4.3 数据库直读（条件）
### 4.4 副本版本语义
| 场景 | 返回值 | 判定 |

## 5. 投影构建与同步路径（仅异构副本）
### 5.1 投影器
### 5.2 裁剪器
### 5.3 源
### 5.4 订阅与路径
| 订阅别名 | 事件 | 动作 |

## 6. 查询设计
### 6.1 读服务
### 6.2 查询族与源对应
| 读服务方法 | 查询族 | 源 | 全量投影 | 裁剪目标 |

### 6.3 分页约定
### 6.4 ES 条件工厂
### 6.5 读路径的一致性边界
```

## 附录 B 代码模板库

**投影根接口与源端口**

```java
public interface I{Agg}Projection extends IAggregateProjection {
}

public interface I{Agg}EsSource extends IProjectionByIdSearcher<{Agg}EsProjection>,
        IOneQuerySearcher<{Agg}EsProjection, {Agg}OneQuery>,
        IPagedQuerySearcher<{Agg}EsProjection, {Agg}PageQuery> {
    // 按实际支撑的族增删：不支撑 List 族就不要 extends IListQuerySearcher

    <X extends I{Agg}Projection> IReducer<{Agg}EsProjection, X> getReducer(Class<X> target);
}

public interface I{Agg}RedisSource extends IProjectionByIdSearcher<{Agg}CacheProjection> {

    <X extends I{Agg}Projection> IReducer<{Agg}CacheProjection, X> getReducer(Class<X> target);
}
```

**条件族**

```java
public record {Agg}OneQuery(String entityId, Long ownerId) implements OneQueryCriteria {
}

public record {Agg}PageQuery(Optional<Long> ownerId,
                             Optional<Integer> status,
                             Optional<LocalDateTime> createdFrom,
                             Optional<LocalDateTime> createdTo) implements PageQueryCriteria {
}

/** 数据库直读参数：普通 record，不实现 criteria 接口。 */
public record My{Agg}Query(Long ownerId, List<Integer> statuses) {
}
```

**副本常量**

```java
public final class {Agg}EsTargets {

    public static final String REPLICA_ID = "es:{agg}";

    public static final String ORDER_INDEX_NAME = "{agg}_index_v1";

    public static final String ORDER_INDEX_ALIAS = "{agg}_index";

    private {Agg}EsTargets() {
    }
}

public final class {Agg}CacheTargets {

    public static final String REPLICA_ID = "redis:{agg}";

    public static final String KEY_PREFIX = "{agg}:";

    public static final Duration TTL = Duration.ofHours(24);

    private {Agg}CacheTargets() {
    }
}
```

**投影器**

```java
@Component
public class {Agg}EsProjector extends AbstractAggregateProjector<{Agg}, {Agg}EsProjection> {

    public {Agg}EsProjector() {
        super({Agg}EsProjection.class);
    }

    @Override
    public {Agg}EsProjection project({Agg} aggregate) {
        {Agg}EsProjection projection = new {Agg}EsProjection();
        projection.setEntityId(aggregate.getEntityId().toString());
        // 枚举降级：setStatus(aggregate.getStatus().getValue())
        //              setStatusName(aggregate.getStatus().getName())
        // 金额原样：setTotalAmount(aggregate.getTotalAmount().getAmount())
        projection.setVersion(aggregate.getOldVersion());
        return projection;
    }
}
```

**裁剪器**

```java
@Component
public class {Agg}{Target}Reducer implements IReducer<{Agg}EsProjection, {Agg}{Target}Projection> {

    @Override
    public Class<{Agg}{Target}Projection> projectionType() {
        return {Agg}{Target}Projection.class;
    }

    @Override
    public {Agg}{Target}Projection reduce({Agg}EsProjection source) {
        if (source == null) {
            return null;
        }
        // 纯字段裁剪
        return new {Agg}{Target}Projection();
    }
}
```

**源**

```java
@Slf4j
@Component
public class {Agg}EsSource extends AbstractProjectionSource<{Agg}, Long, {Agg}EsProjection>
        implements I{Agg}EsSource {

    public {Agg}EsSource({Agg}EsProjector projector, {Agg}{Target}Reducer reducer) {
        super(ProjectionSource.of({Agg}EsTargets.REPLICA_ID),
                {Agg}.class,
                {Agg}EsProjection.class,
                projector,
                List.of(reducer));
    }

    @Override
    public void materialize(IAggregateProjection projection, long version) {
        {Agg}EsProjection esProjection = ({Agg}EsProjection) projection;
        // 索引写入：主键 entityId，External 版本 version，409 冲突 log.debug 丢弃，真实失败上抛
    }

    @Override
    public void purge(Object aggregateId) {
        // 按主键删除文档，不存在视为幂等
    }

    @Override
    public long readVersion(Long aggregateId) {
        // 文档缺失返回 0，不返回 -1
        return 0L;
    }

    @Override
    public void rebuild(Long aggregateId) {
        // findById → sync；写模型不存在则 purge
    }
}
```

**订阅者**

```java
@Component
public class {Agg}DataSyncEsHandle implements I{Agg}DataSyncEsHandle {

    @Override
    public void handleEvent({Agg}DataSyncEvent event) {
        Long aggregateId = Long.valueOf(event.getEntityId());
        if (event.getDataSyncAction() == DataSyncAction.PURGE) {
            this.{agg}EsSource.purge(aggregateId);
            return;
        }
        {Agg} aggregate = this.{agg}Repository.findById(aggregateId);
        if (aggregate == null) {
            this.{agg}EsSource.purge(aggregateId);
            return;
        }
        this.{agg}EsSource.sync(aggregate);
    }
}
```

**读服务**

```java
@Service
public class {Agg}ReadService implements IQueryApplicationService {

    private final I{Agg}RedisSource {agg}RedisSource;

    private final I{Agg}EsSource {agg}EsSource;

    /** 详情：按标识读缓存副本，未命中返回 null，不回退 ES。 */
    public {Agg}DetailProjection getDetail(Long aggregateId) {
        {Agg}CacheProjection cached = this.{agg}RedisSource.getById(aggregateId);
        if (cached == null) {
            return null;
        }
        IReducer<{Agg}CacheProjection, {Agg}DetailProjection> reducer =
                this.{agg}RedisSource.getReducer({Agg}DetailProjection.class);
        if (reducer == null) {
            throw new ProjectionReducerNotFoundException("{Agg} 详情裁剪器未注册");
        }
        return reducer.reduce(cached);
    }
}
```

## 附录 C 框架机制参考

本文档只引用、不重复框架机制：

- [投影读模型设计](/best-practices/projection-design)：四角色、落地方式、关键约束与反模式
- [投影基类 API](/best-practices/projection-api)：可编译方法签名（零上下文场景必读）
- [ES 配置](/best-practices/elasticsearch-config)：RestClient 单 Bean、投影写入、限流
- [事件订阅领域服务](/best-practices/event-subscriber-pattern) / [事件建模](/best-practices/event-modeling)
- [枚举值处理](/best-practices/enum-value)：条件族与投影 DTO 中枚举降级
- [聚合写模板](/best-practices/design-doc-write-template)
