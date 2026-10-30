---
name: ddd-project-init
description: 初始化一个采用 Pragmatic DDD 框架的新 Maven 工程：生成 pom.xml（依赖 pragmatic-ddd-bom + 内核/MyBatis/RocketMQ/Outbox 模块）、四层目录骨架、主启动类、application.properties 模板与 .ddd/project-context.md 项目上下文徽章。当用户说「新建一个 DDD 项目」「初始化工程脚手架」「接入 pragmatic-ddd 框架」时触发。只做初始化，业务聚合交给 ddd-aggregate-scaffold / ddd-aggregate-create。
---

# ddd-project-init（项目初始化）

> 类型：S1（初始化类）
> 版本：v2.0 · 适用框架：2.1.0-SNAPSHOT
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）

## 1. 定位

从零搭建一个采用 Pragmatic DDD 的 Maven 工程：依赖装配 + 四层目录骨架 + 主启动类 + 配置模板 + 项目上下文徽章。不含任何业务聚合代码（交给 S2/S4）。

## 2. 输入

- 工程坐标 `groupId` / `artifactId`（必填，向用户确认）。
- 模块选型：内核（必选）+ MyBatis（默认）/ RocketMQ（默认）/ Outbox（可选）/ Kafka（可选）。
- 数据库与 MQ 连接信息（仅写入 `application.properties` 占位模板，敏感信息用 `${ENV}` 占位，**禁止硬编码**）。

## 3. 工作流程

### 3.1 生成 pom.xml

- 父 pom 引入 `pragmatic-ddd-bom` 统一版本管理。
- 依赖：`pragmatic-ddd-core`（必选）、`pragmatic-ddd-mybatis`（默认）、`pragmatic-ddd-rocketmq`（默认）、`pragmatic-ddd-spring-boot`（装配）。
- 可选：`pragmatic-ddd-kafka`、`pragmatic-ddd-outbox`（与 RocketMQ 互斥时二选一）。
- Java 17（`<maven.compiler.release>17</maven.compiler.release>`），Lombok `provided`。
- 不写版本号（交 BOM 管理）。

### 3.2 生成四层目录骨架

按 `kb/bp/aggregate-structure.md` §3 建 `api/controller/application/domain/infrastructure` 根包（空包 + `package-info.java` 占位）。`domain` 层零基础设施依赖（硬约束 9）。

### 3.3 生成主启动类

- `@SpringBootApplication` 主类 `Application`，包根与 `domain` 同级（组件扫描覆盖全工程）。
- 不在此类写业务 Bean。

### 3.4 生成 application.properties 模板

- 数据库 / RocketMQ / MyBatis 占位配置，敏感信息全部 `${ENV_VAR}` 占位（安全红线：禁止硬编码密码/连接串）。
- 标注哪些是占位、哪些需用户填真实值。

### 3.5 生成 .ddd/project-context.md

项目上下文徽章（根目录 `.ddd/`），记录：技术栈、模块选型、根包、ID 策略、约定（小聚合 / 出参 DTO / 事件驱动 / 聚合根禁 `@Data`）。供 AI 后续步骤快速加载。模板见 `kb/bp/aggregate-structure.md`。

### 3.6 初始化设计文档工作区

在目标工程根目录执行能力包内的 `scripts/design-doc-workspace.sh init`（落点自动指向当前工程），产出：

```text
docs/design/
├── prerequisites.md      ← 10 项全「待确认」，不预填默认值
├── changes/active/
├── changes/archive/
└── aggregates/
```

理由：数据库 / 总线 / Outbox 等选择在 S3 装配时才用，但必须在项目起点就形成待确认清单与目录骨架，否则 S3 / S4 会被迫「默认选型」，设计文档也无处安放。

目录约定见 `docs/design/agent-skills/文档产物目录规划-v2.md`。

### 3.7 自检 + 交付

- 核对：BOM 管理版本、目录与 `aggregate-structure.md` 一致、properties 无硬编码秘密。
- 提示：业务聚合用 S2 搭骨架、S4 填逻辑；基础设施详配用 S3。

## 4. 生成规则

- pom 不写死版本；模块按用户选型增删。
- 目录以聚合为第一级（基础设施以类型为第一级），见 `aggregate-structure.md` §2。
- 所有连接串/密钥用环境变量占位。

## 5. 强制约束

- 硬约束 9（`domain` 零基础设施依赖）在骨架阶段天然满足，业务填充时 S4 保证。
- 安全：禁止硬编码 API Key/密码/内部 IP（项目全局红线）。
- Java 17 语法优先（records、sealed、Optional、Stream）。

## 6. 检查清单

- [ ] pom 引入 BOM，无写死版本
- [ ] 四层空目录存在且可编译
- [ ] 主启动类与根包一致
- [ ] application.properties 敏感项全 `${ENV}` 占位
- [ ] `.ddd/project-context.md` 已生成
- [ ] 交付提示指向 S2/S3/S4

## 7. 防幻觉

- 模块坐标以 `pragmatic-ddd-bom` 实际 artifactId 为准；不确定先 Read 仓库根 `pom.xml` / `pragmatic-ddd-bom/pom.xml`。
