# BP 卡：Elasticsearch 配置设计原则（elasticsearch-config）

> 源：`documentation/best-practices/elasticsearch-config.md` · 派生时间：2026-09-19 · 框架版本：2.0.0

## 1. 关键约定

- 集中式 `@Configuration`（如 `ElasticSearchConfig`）统一提供 `RestClient`/`ElasticsearchTransport`/`ElasticsearchClient` 三 Bean；索引名/对账目标等寻址常量另置 `{Agg}EsTargets` 常量类。
- 连接参数（nodes/账号/密码/超时）一律外部化（`elasticsearch.*`），**代码不硬编码密钥**；密码经环境变量注入。
- 基于官方 `elasticsearch-java` 客户端，**不引入 Spring Data Elasticsearch、不依赖自动装配**，三层对象全手写。
- 配置与使用分离：配置产 Bean，源（`{Agg}EsSource`）/读服务消费 `ElasticsearchClient`。

## 2. 三层对象模型（每层独立 Bean）

```text
RestClient（Apache HttpClient：连接/多节点/认证/超时）
  └─ ElasticsearchTransport（JSON 序列化，JacksonJsonpMapper）
       └─ ElasticsearchClient（类型安全高层客户端，业务注入）
```

- 多节点：`hosts` 逗号分隔 → 流 `trim`/`filter`/`HttpHost::create`/`toList`。
- 认证：`Optional` 表达「有则挂载、无则跳过」，**不用三元表达式**。
- `RestClient`、`ElasticsearchClient` 均 `Closeable`，必须 `@Bean(destroyMethod = "close")`；`Transport` 由 Client 级联关闭，无需单独声明。
- 集合处理用流（见 §2.1）。

## 3. 投影物化：External 版本乐观并发

- 写模型版本号写入 ES `_version`（`VersionType.External`），实现跨存储乐观并发与对账。
- 版本读取由源承载（`IReadModelReplica.readVersion`）：文档缺失返回 `0`（判 STALE 触发 rebuild）、`-1` 未追踪（判 UNTRACKED 不重建）；**误用 -1 使副本永久落后**。
- 重建由源承载（`IReadModelReplica.rebuild`）：`findById` 取写模型快照重新物化。

## 4. 失败区分（重要）

- **External 版本冲突（409 / `VersionConflictEngineException`）**：迟到/重复事件，`_version` 不前进，属正常乐观锁拒绝 → **静默丢弃**（仅 `log.debug`），不触发 resync。
- **真正写失败（连接断开/映射错误/集群不可用）**：**上抛**（`IOException` 包装为运行时异常），交上层重试/对账补偿；不可静默吞掉（掩盖副本真正落后）。

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 连接参数硬编码 | 外部化 `elasticsearch.*` |
| 引入 Spring Data ES 自动装配 | 官方 `elasticsearch-java` 显式声明 |
| 三层混一 Bean | 按 RestClient→Transport→Client 分层 |
| 多节点硬编码单地址 | 逗号分隔+流解析 |
| 条件判断用三元 | 用 `Optional` |
| RestClient/Client 未 `destroyMethod="close"` | 声明 `close` |
| 真写失败 catch 静默吞 | 上抛交对账 |
| 409 冲突上抛 | `ResponseException` 仅 `log.debug` |
| 索引名散落硬编码 | 集中 `{Agg}EsTargets` |
| 用 ES 自增 `_version` | `VersionType.External` 写写模型版本 |

## 6. 被读于

S3 `ddd-infra-config`（ES 装配）、S5 `ddd-projection-create`。
