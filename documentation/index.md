---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "Pragmatic DDD"
  text: "Pragmatic DDD 是一个以\"务实\"为设计哲学的领域驱动设计（DDD）框架"
  tagline: 纯 Java、零框架依赖、SPI 驱动 —— 核心模块仅依赖 Lombok，不绑定任何框架
  actions:
    - theme: brand
      text: 快速开始
      link: /markdown-examples
    - theme: alt
      text: 设计文档
      link: /api-examples
  image:
    src: /hero-logo.svg
    alt: Pragmatic DDD

features:
  - icon: 🧩
    title: 零框架依赖核心
    details: core 模块仅依赖 Lombok（provided），不绑定 Spring、MyBatis 或任何消息中间件。通过端口-适配器模式（Hexagonal Architecture）将基础设施实现延迟到独立集成包中，可被任意 Java 项目引用。
  - icon: 🏗️
    title: 聚合根与规则引擎
    details: 聚合根基类内聚规则校验、事件收集、操作追踪。规则引擎支持双参数校验（新旧模型对比）、消息码注册表自动扫描、两级激活条件（运维级 + 业务级）与运行时动态增删改，是同类框架中最完整的规则校验体系。
  - icon: 📨
    title: 领域事件体系
    details: 即时/延迟事件分离（Supplier 惰性求值）、订阅者依赖图 + 拓扑排序精确声明执行顺序、双重条件过滤（switchCheck 运维开关 + status 业务条件）、定向发布与投递策略控制。本地线程池实现支持延时投递与失败重试。
  - icon: ✅
    title: Outbox 可靠投递
    details: 事务内持久化保证与聚合根同事务原子性，claim_token 机制确保多实例部署无重复投递。完整状态机管理（PENDING → PROCESSING → SENT / FAILED），支持重试、死信与 grace 窗口，实现真正的 at-least-once 投递。
  - icon: 🔢
    title: 号段 ID 生成
    details: 内建完整号段模式（Leaf-Segment）ID 生成体系，支持 Long 与 String（带前缀/格式化）两种类型、多业务 bizKey 隔离、数据库行锁保证并发安全。注册中心支持从配置源批量加载生成器定义。
  - icon: 🔌
    title: 多基础设施适配
    details: MyBatis（Outbox Store / 号段分配器 / 枚举·JSON·集合 TypeHandler）、RocketMQ（Remoting 4.x + gRPC 5.x 双协议）、Kafka、Spring Boot 可插拔适配。SPI 端口驱动，基础设施实现可自由替换。
---

