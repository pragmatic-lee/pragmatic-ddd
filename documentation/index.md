---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Pragmatic DDD"
  text: "以\"务实\"为哲学的轻量级 DDD 框架"
  tagline: 不套用教条，只交付可落地的领域建模、规则校验与可靠事件能力 —— 核心库零业务侵入、SPI 驱动
  actions:
    - theme: brand
      text: 快速开始
      link: /getting-started/quick-start
    - theme: alt
      text: 核心概念
      link: /core/domain-modeling
  image:
    src: /hero-logo.svg
    alt: Pragmatic DDD

features:
  - icon: 🧱
    title: 务实领域建模
    details: 实体 / 值对象 / 聚合根按需取用，不强制分层教条，降低 DDD 落地门槛。框架提供构件而非约束，让团队像搭积木一样组装领域模型。
  - icon: ✅
    title: 完整业务规则引擎（业界独有）
    details: 把"校验"升级为带新旧模型双参数对比、消息码注册表、两级动态激活、运行时增删改的企业级规则引擎，让不变量集中可审计、可热更。同类 DDD 框架中几乎没有对标物。
  - icon: 🔁
    title: 领域操作与变更追踪
    details: 统一命令式操作标记（CREATE/UPDATE/DELETE）+ 精准脏数据增量持久化（TrackedMap / TrackedList 三桶设计），持久化从"全删全插"变为"增量同步"。
  - icon: 🛡️
    title: 防腐层与外部依赖隔离
    details: ExternalDependencyDeclaration 声明式解耦外部系统，组合式（ExternalCall）与继承式（Abstract*Gateway）双模式并存，覆盖从简单到复杂的全谱场景。
  - icon: 📨
    title: 可靠领域事件与有序编排
    details: 基于依赖边图的订阅者执行顺序声明（偏序声明 / 注册期循环检测 / 无依赖分支可并行），配合双重条件过滤、即时/延迟事件分离与事务性发件箱（RocketMQ，Kafka 规划中）。
  - icon: 🔌
    title: 框架而非脚手架
    details: 核心库零业务侵入，SPI 端口驱动，MyBatis / RocketMQ 实现已落地、Kafka / Spring Boot 集成规划中，实现可插拔替换，可被任意 Java 项目引用。
---

## 为什么是 Pragmatic DDD

Pragmatic DDD 选择了一条更底层的路——**纯 Java、SPI 驱动、不绑定任何框架**。核心模块仅依赖 Lombok（provided），所有基础设施能力（事件管理、Outbox 存储、ID 分配、配置源、Topic 解析、序列化）全部定义为以 `I` 开头的端口接口，实现推迟到独立的集成包。这让框架可被任意 Java 项目引用，不引入任何传递依赖冲突，也不强迫团队接受框架约定。

与 Spring Modulith、Axon Framework、COLA 等业界框架相比，Pragmatic DDD 在几个关键维度上具备差异化价值：

- **规则引擎（业界独有）**：双参数校验（新旧模型对比）、消息码注册表自动扫描、运维级 + 业务级两级动态激活，把"校验"提升为企业级不变量守护，而非简单的 `Validator` 接口。
- **事件依赖图**：订阅者间的执行顺序用有向图 + 拓扑排序做偏序声明（支持循环检测、可并行），比 Spring `@Order` 的全序排列更精确、更健壮。
- **防腐层双模式**：组合式 `ExternalCall` 与继承式 `Abstract*Gateway` 并存，配合声明式外部依赖描述，系统化隔离外部系统。
- **号段 ID 生成**：内建完整的 Leaf-Segment 模式（Long / String 双类型、多业务隔离），填补了 DDD 框架在基础设施层面的常见空白。

更多设计理念与决策背景，请参阅 [设计理念](/getting-started/design-philosophy)。

## 快速导航

- [指南](/getting-started/overview)：框架概览、设计理念、快速开始、推荐项目结构
- [核心概念](/core/domain-modeling)：领域建模、领域服务、领域事件、业务规则、应用服务、仓储、变更追踪、防腐层、配置体系、对外广播
- [集成](/integration/mybatis)：MyBatis 集成、RocketMQ 集成
- [最佳实践](/best-practices/)：聚合目录落地骨架、聚合设计原则、领域服务落地模式、应用层落地模式、仓储设计、投影读模型、配置装配、事件建模
- [参考](/reference/api-index)：API 速查索引、配置项参考
