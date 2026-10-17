---
name: ddd-infra-config
description: 为 Pragmatic DDD 工程装配基础设施：MySQL 数据源、MyBatis SqlSessionTemplate、RocketMQ/Outbox 事件总线、Elasticsearch 源、Redis 源等配置类，以及聚合根/子实体建表 SQL。当用户说「配置 MySQL/MyBatis/RocketMQ」「接 Outbox 事务消息」「建表 SQL」「配置 ES/Redis 源」时触发。配置类放在 infrastructure/config，通用配置单一来源，聚合级配置放 config/{agg}。不改业务代码，只做技术装配。
---

# ddd-infra-config（基础设施配置）

> 类型：S3（基础设施配置类）
> 版本：v1.0-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）

## 1. 定位

把框架运行所需的技术能力装配进 Spring 容器：数据源、ORM、事件总线、索引/缓存源。配置即"插头"，业务逻辑在领域/应用层（不在此写业务）。

## 2. 输入

- 已初始化的工程（S1 产物）。
- 待装配能力清单：MySQL + MyBatis（默认）/ RocketMQ / Outbox / ES 源 / Redis 源。
- 真实连接信息：仅填 `application.properties` 的 `${ENV}` 占位（不硬编码）。

## 3. 工作流程

### 3.1 数据源与 MyBatis

职责分两层，两个配置类单向依赖（`MyBatisConfig → MySqlConfig`）：

- `infrastructure/config/MySqlConfig`：只产 `DataSource`（Hikari）与 `PlatformTransactionManager`，开 `@EnableTransactionManagement`；不 import 任何 MyBatis 类型。连接参数读 `application.properties` 的 `${ENV}` 占位。
- `infrastructure/config/MyBatisConfig`：产 `SqlSessionFactory` 与 `SqlSessionTemplate`；用原生 `Configuration` 注入各聚合 `TypeHandlerContext`（入参必须 `List<TypeHandlerContext>`）， **不用 `setConfigLocation` / `mybatis-config.xml`**。
- Mapper XML 必须用 `classpath*:` 扫四路：框架三组（`io/pragmatic/ddd/mybatis/{outbox,id,compensation}/*.xml`）+ 业务 `mapper/**/*.xml`；用单 `classpath:` 扫不到 jar 内框架 XML。
- 通用配置放 `infrastructure/config/`（单一来源），聚合级配置放 `infrastructure/config/{agg}/`。
- 不注解 `@MapperScan` 扫描 Java 接口（本框架 Mapper 为纯 XML，`SqlSessionTemplate` 用 `namespace` 查）。

### 3.2 事件总线

- RocketMQ：`RocketMQConfig` 装配 `IEventManager`（发布 + 订阅注册中心 `IEventRegistry`）；订阅者显式 `registerSubscriber`（见 `event-subscriber-pattern.md`，框架不扫 `@Component`）。
- Outbox：选 Outbox 场景配 `OutboxCommandExecutor` + `OutboxUnitOfWork` + `Supplier<IUnitOfWork>`，与应用层 `AbstractApplicationService` 注入的执行器一致；Outbox 与 RocketMQ 直发互斥二选一。

### 3.3 索引 / 缓存源

- ES 源：`ElasticsearchConfig` + 源 Bean（投影读模型用，见 `projection-design.md`）。
- Redis 源：缓存副本源 Bean（同投影扩展模式）。

### 3.4 建表 SQL

- 按 `kb/bp/aggregate-database-design.md` §4 生成：主表（`entityId` 主键 + `version` + 审计列）、子实体表（`id` 自增 + 逻辑外键 `order_id` 普通索引）、值对象 JSON 列、投影副本表（`REPLICA_ID` 唯一、副本列）。
- 用到框架能力时还需建框架表（脚本随 `pragmatic-ddd-mybatis` jar 发布，直接取出执行）：Outbox → `outbox_message`；补偿 durable → `compensation_log`；DB 号段 ID → `id_segment`。
- SQL 落在 `infrastructure/.../schema/*.sql`（或 `resource/`），与仓储 XML 同模块。

### 3.5 自检 + 交付

- 核对：执行器/工作单元装配与应用层 `AbstractApplicationService` 构造一致；敏感值全占位；建表含 `version`/审计列。
- 提示：业务聚合用 S4 填仓储实现 + Mapper XML（见 `repository-design.md`）。

## 4. 生成规则

- 配置类 `@Configuration`（通用）或 `@Component`（源 Bean），按层放置。
- 通用配置单一来源，聚合级隔离；不重复声明同 Bean。
- 订阅注册显式一行一条（别名 + 事件类 + 实例）。

## 5. 强制约束

- 安全红线：数据库/MQ/ES 连接串与密钥**全部** `${ENV}` 占位，禁止硬编码。
- 硬约束 9：配置在 `infrastructure`，`domain` 层不出现任何 Spring/Mapper/MQ 导入。
- 事件版本语义：物化订阅取 `event.getVersion()`（事件路径），对账取 `getOldVersion()`（见 `event-subscriber-pattern.md`）。

## 6. 检查清单

- [ ] 数据源/SqlSessionTemplate 装配完成（`MySqlConfig` + `MyBatisConfig` 分开）
- [ ] 框架固定 Mapper XML 已扫入（outbox / id / compensation，`classpath*:`）
- [ ] 事件管理器/注册中心注入
- [ ] Outbox 或 RocketMQ 二选一，未混用
- [ ] ES/Redis 源 Bean 就位（若需）
- [ ] 建表 SQL 含 version + 审计列 + 逻辑外键索引；所用框架能力的表已建
- [ ] 所有秘密 `${ENV}` 占位
- [ ] domain 层零基础设施依赖（硬约束 9）

## 7. 防幻觉

- 配置类/Bean 名以 `kb/bp/mysql-config.md` / `kb/bp/mybatis-config.md` / `kb/bp/outbox-config.md` / `kb/bp/rocketmq-config.md` / `kb/bp/elasticsearch-config.md` / `kb/bp/event-manager-config.md` 为准；不确定先 Read 对应卡，或向用户确认其自备实现。KB 自足，不依赖外部示例工程。
