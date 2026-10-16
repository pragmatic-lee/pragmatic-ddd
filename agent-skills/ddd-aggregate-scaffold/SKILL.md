---
name: ddd-aggregate-scaffold
description: 为 Pragmatic DDD 新聚合生成「极简目录骨架」——仅 `domain/{agg}` 与 `application/{agg}` 的空包结构，不含任何业务代码。当用户说「建个聚合骨架/目录」「搭个 {agg} 的包结构」「初始化 DDD 分层目录」时触发。不生成充血逻辑，只搭骨架；业务代码交给 ddd-aggregate-create。
---

# ddd-aggregate-scaffold（聚合骨架生成）

> 类型：S2（初始化 / 项目骨架类）
> 版本：v1.0-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）

## 触发

用户意图 = 搭目录骨架，不写业务逻辑。例如：「给 {agg} 建个聚合骨架」「初始化 {agg} 的 DDD 分层目录」。

## 不做什么（边界）

- 不生成充血方法、规则、事件、仓储实现——那是 S4 `ddd-aggregate-create` 的职责。
- 不生成 Controller / 基础设施配置——那是 S1/S3 职责。

## 执行步骤

### 步骤 1：确认聚合名与根包

- 向用户确认聚合名 `{agg}`（如 `Order`）与根包（如 `io.pragmatic.ddd.example.order`）。
- 若用户已给，直接进入步骤 2。

### 步骤 2：产出极简目录骨架

只产出**空目录 + 占位包**（无业务代码），结构如下（硬约束 9：`domain` 零基础设施依赖）：

```text
{root}/domain/{agg}/
├── model/                 # 聚合根 / 子实体 / 值对象（待 S4 填）
├── operation/             # 操作注册表（待 S4）
├── rule/                  # 规则注册表 + 规则容器 + 装配（待 S4）
├── repository/            # 领域层仓储抽象（待 S4）
├── param/                 # 内部参数对象（待 S4）
├── event/                 # 领域事件（待 S4）
└── service/               # 领域服务契约（待 S4）

{root}/application/{agg}/
├── input/                 # 各操作 Input（待 S4）
├── factory/               # EntityFactory（待 S4）
├── updater/               # EntityUpdater（待 S4）
├── rule/                  # 规则装配 OrderRuleConfig（待 S4）
├── service/               # 领域服务实现（待 S4）
├── subscriber/            # 事件订阅登记（待 S4，可选）
├── {Agg}WriteService.java # 命令应用服务（待 S4）
└── {Agg}ReadService.java  # 查询应用服务（待 S4）
```

- 包结构依据：`kb/bp/aggregate-design.md` §1 + `kb/bp/application-collaboration.md` §1。
- 每个目录下建一个空的 `package-info.java`（或占位 `.gitkeep`），保证包存在、可编译。

### 步骤 3：自检 + 交付

- 核对 `kb/hard-constraints.md` 约束 9（domain 层无 Spring/MyBatis/MQ 导入）；骨架阶段无业务代码，天然满足。
- 提示用户：业务逻辑用 S4 `ddd-aggregate-create` 填充；基础设施配置用 S3 `ddd-infra-config`。

## 防幻觉

- 目录命名以 `kb/bp/aggregate-design.md` §1 为准；不确定先 Read `kb/api-contract.md` 或 `kb/bp/aggregate-structure.md`。

## 验收

- 产出的目录与 `kb/bp/aggregate-design.md` §1 / `application-collaboration.md` §1 一致。
- `mvn compile` 在补完 S4 业务前不要求通过（空包即可）。
