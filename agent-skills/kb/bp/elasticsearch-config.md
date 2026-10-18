# BP 卡：Elasticsearch 配置设计原则（elasticsearch-config）

> 源：`documentation/best-practices/elasticsearch-config.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 集中式配置与三层对象模型

一个模块一个 `ElasticSearchConfig` `@Configuration`，统一提供 `RestClient` / `ElasticsearchTransport` / `ElasticsearchClient` 三个 Bean；索引名/对账目标等寻址常量另置 `*Targets` 常量类。

```text
RestClient                  低层 HTTP（Apache HttpClient）：连接/多节点/认证/超时
  └─ ElasticsearchTransport 传输契约：JSON 序列化（JacksonJsonpMapper）
       └─ ElasticsearchClient 高层类型安全客户端：供仓储与查询服务注入
```

- 基于官方 `elasticsearch-java` 客户端，**不引入 Spring Data ES、不依赖其自动装配**。
- 连接参数（`elasticsearch.hosts`/`username`/`password`/`connect-timeout`/`socket-timeout`）从外部化配置经 `@Value` 注入，禁止硬编码。
- 配置与使用分离：源（`AbstractProjectionSource` 子类，如 `OrderEsSource`）/ 读服务消费 `ElasticsearchClient`。

## 2. 三层客户端构建

### 2.1 RestClient：多节点 + 可选 Basic 认证 + 超时

```java
private List<HttpHost> parseHosts() {
    return Arrays.stream(hosts.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(HttpHost::create)
            .toList();
}

private Optional<RestClientBuilder.HttpClientConfigCallback> credentialsProvider() {
    if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
        return Optional.empty();
    }
    BasicCredentialsProvider provider = new BasicCredentialsProvider();
    provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    return Optional.of(builder -> builder.setDefaultCredentialsProvider(provider));
}
// 调用：credentialsProvider().ifPresent(builder::setHttpClientConfigCallback);  // 不用三元表达式
builder.setRequestConfigCallback(rc -> rc.setConnectTimeout(connectTimeout).setSocketTimeout(socketTimeout));
```

### 2.2 Transport

```java
@Bean
public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
}
```

### 2.3 Client（必须 destroyMethod = "close"）

```java
@Bean(destroyMethod = "close")
public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
    return new ElasticsearchClient(transport);
}
```

| 项 | 取值 | 说明 |
| --- | --- | --- |
| `RestClient`/`ElasticsearchClient` 销毁方法 | `close` | 关闭连接池；`ElasticsearchTransport` 由 Client 级联关闭，无需声明 |
| 建连超时 | `elasticsearch.connect-timeout` 默认 3000ms | 防线程无限阻塞 |
| 数据传输超时 | `elasticsearch.socket-timeout` 默认 30000ms | 单次读响应上限 |

## 3. 装配规范与投影物化

- 条件装配用 `Optional`（如认证），**不用三元表达式**；集合处理用流（节点解析）。
- **投影物化：External 版本乐观并发控制**——写模型版本号写入 ES `_version`（`VersionType.External`），跨存储对账：

```java
elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
        .id(projection.getOrderId().toString())
        .versionType(VersionType.External)
        .version(version)          // 写模型副本版本 V
        .document(projection));
```

- 版本读取由源自身承载（`IReadModelReplica.readVersion`）：读 `_version` 作 V'；文档缺失返回 `0`（判 STALE 触发 rebuild）、`-1` 表示未追踪（判 UNTRACKED 不重建）；误用 `-1` 使副本永久落后。
- 重建由源承载（`IReadModelReplica.rebuild`）：`findById` 取写模型当前快照重新物化。

### 3.1 区分「版本冲突」与「写失败」

- **External 版本冲突（409，`ResponseException`）**：新事件已先于旧事件到达，旧事件迟到，属正常乐观锁拒绝，**静默丢弃**（`log.debug`），无需 resync。
- **真正写失败（连接/映射/集群不可用）**：**不可**静默吞掉（会掩盖副本真正落后），让异常上抛（`IOException` 包运行时异常），交上层事件重试/对账（resync/purge）兜底。
- `purge` 同理：`delete` 文档不存在抛 `ResponseException` 静默忽略（清理幂等）。

## 4. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 连接参数硬编码 | 从 `elasticsearch.*` 外部化配置读 |
| 引入 Spring Data ES 自动装配 | 官方 `elasticsearch-java` 显式声明 |
| 三层对象混一个 Bean | 按 RestClient→Transport→Client 分层 |
| 多节点硬编码单地址 | 逗号分隔 + 流式解析 |
| 条件判断用三元表达式 | 用 `Optional` |
| `RestClient`/`Client` 未声明 `destroyMethod` | 声明 `destroyMethod="close"` |
| 真正写失败 catch 静默吞 | 异常上抛交对账补偿 |
| 409 版本冲突上抛 | 捕获 `ResponseException` 仅 `log.debug` 丢弃 |
| 索引名散落硬编码 | 集中 `*Targets` 常量类 |
| 用 ES 内部自增 `_version` | `VersionType.External` 写入写模型版本 |

## 5. 被读于

S3 `ddd-infra-config`、S5 `ddd-aggregate-extend`、S6 `ddd-projection-create`、S7 `ddd-code-review`。
