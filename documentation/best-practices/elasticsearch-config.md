# Elasticsearch 配置设计原则

> 本文档介绍使用 Pragmatic DDD 进行 Elasticsearch 客户端配置的最佳实践与常见反模式：先明确配置类的定位与职责边界，再说明 RestClient → Transport → Client 三层对象模型的构建要点与认证/超时装配机制，最后落到客户端装配规范与投影物化配套。

## 1. Elasticsearch 配置设计原则

### 1.1 集中式配置类：一个模块一个配置

ES 客户端配置应集中在一个 `@Configuration` 类中（如 `ElasticSearchConfig`），统一提供 `RestClient`、`ElasticsearchTransport`、`ElasticsearchClient` 三个 Bean。配置不散落在各处，便于审查连接参数、认证信息与超时设置。

```java
@Configuration
public class ElasticSearchConfig {
    // RestClient / ElasticsearchTransport / ElasticsearchClient 三 Bean
}
```

**设计含义**：配置类只负责装配基础设施 Bean，不包含任何业务逻辑；索引名、对账目标等投影寻址常量另置于 `OrderEsTargets` 等常量类（见 §3.4）。

### 1.2 外部化配置，不硬编码

连接参数（节点地址 / 账号 / 密码 / 超时）一律从外部化配置（`application.properties` 或环境变量）读取，通过 `@Value` 注入并给出默认值，**代码中不出现任何连接串与密钥**。

```java
@Value("${elasticsearch.hosts:http://localhost:9200}")
private String hosts;

@Value("${elasticsearch.username:}")
private String username;

@Value("${elasticsearch.password:}")
private String password;
```

> 对应 `application.properties`：`elasticsearch.hosts=http://localhost:9200`、`elasticsearch.username=...`、`elasticsearch.password=...`、`elasticsearch.connect-timeout=3000`、`elasticsearch.socket-timeout=30000`。密码在示例中经环境变量注入，禁止在 Java 里硬编码。

### 1.3 不依赖自动配置，显式声明

本项目基于官方 `elasticsearch-java` 客户端，**不引入 Spring Data Elasticsearch，也不依赖其自动装配**。RestClient、Transport、Client 三层对象全部由本配置类手写提供，装配过程可见、可审查，避免隐式自动装配带来的不可预期性。

### 1.4 三层对象模型：RestClient → Transport → Client

官方客户端按「传输层 → 应用层」拆分为三层对象，职责各不相同，**每层都是独立 Bean、独立构建**：

```text
RestClient                 低层 HTTP 客户端（Apache HttpClient），管理连接、多节点、认证、超时
  └─ ElasticsearchTransport 传输契约，封装 JSON 序列化（Jackson）与请求发送
       └─ ElasticsearchClient 高层类型安全客户端，供仓储与查询服务注入使用
```

**设计含义**：分层便于替换单点（如换 JSON 序列化器只动 Transport），也便于各层独立测试与复用。

### 1.5 配置与使用分离

配置类产出 Bean，物化器 / 版本解析器 / 查询服务消费 Bean（`ElasticsearchClient`）。使用方只关心"怎么用"，不关心"怎么建"；配置只关心"怎么建"，不关心"怎么查"。

```java
@Bean
public OrderEsMaterializer orderEsMaterializer(ElasticsearchClient elasticsearchClient) {
    return new OrderEsMaterializer(elasticsearchClient);
}
```

---

## 2. 三层客户端构建专题

### 2.1 低层 RestClient：多节点解析与 Basic 认证

`RestClient` 负责与 ES 集群的实际 HTTP 通信。构建要点有二：**多节点逗号分隔解析**与**可选 Basic 认证注入**。

**多节点解析**用流处理：`hosts` 按逗号切分后逐个 `trim`、过滤空串、转 `HttpHost`，天然支持集群地址。

```java
private List<HttpHost> parseHosts() {
    return Arrays.stream(hosts.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(HttpHost::create)
            .toList();
}
```

**认证注入**按配置动态决定：用户名密码任一为空即不启用认证，否则构造 `BasicCredentialsProvider` 并返回配置回调，交由 `ifPresent` 有条件挂载——**不写三元表达式**（见 §3.1）。

```java
credentialsProvider().ifPresent(builder::setHttpClientConfigCallback);

private java.util.Optional<RestClientBuilder.HttpClientConfigCallback> credentialsProvider() {
    if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
        return java.util.Optional.empty();
    }
    BasicCredentialsProvider provider = new BasicCredentialsProvider();
    provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
    return java.util.Optional.of(httpClientBuilder ->
            httpClientBuilder.setDefaultCredentialsProvider(provider));
}
```

**超时参数**经 `RequestConfig` 回调统一注入：`setConnectTimeout` 控制建连，`setSocketTimeout` 控制数据传输。

```java
builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
       .setConnectTimeout(connectTimeout)
       .setSocketTimeout(socketTimeout));
```

### 2.2 ElasticsearchTransport：JSON 序列化契约

`ElasticsearchTransport` 是连接「低层 HTTP」与「高层类型安全 API」的契约层，核心是 JSON 序列化器。默认用 `JacksonJsonpMapper`，与 Spring 生态的 Jackson 保持一致；需要自定义序列化策略时，仅需替换此 Mapper。

```java
@Bean
public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
}
```

### 2.3 ElasticsearchClient：类型安全客户端与生命周期

`ElasticsearchClient` 是供业务代码注入的高层客户端，所有请求走类型安全的 lambda DSL。两个 Bean 均实现了 `Closeable`，**必须声明 `destroyMethod = "close"`** 以便容器关闭时释放底层连接池（见 §3.3）。

```java
@Bean(destroyMethod = "close")
public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
    return new ElasticsearchClient(transport);
}
```

### 2.4 生命周期与超时管理

| 项 | 取值 | 说明 |
| --- | --- | --- |
| `RestClient` 销毁方法 | `close` | 关闭 Apache HttpClient 连接池 |
| `ElasticsearchClient` 销毁方法 | `close` | 关闭传输层与底层连接 |
| 建连超时 | `elasticsearch.connect-timeout`，默认 `3000ms` | 与 MySQL `connectTimeout` 对齐，防止线程无限阻塞 |
| 数据传输超时 | `elasticsearch.socket-timeout`，默认 `30000ms` | 单次请求读响应上限 |

---

## 3. 装配规范与投影物化配套

### 3.1 条件装配用 Optional，不用三元表达式

是否启用 Basic 认证属于条件判断，按项目规范**不用三元表达式**，而是用 `Optional` 表达"有则挂载、无则跳过"：

```java
// ✅ 推荐：Optional 表达可选认证
credentialsProvider().ifPresent(builder::setHttpClientConfigCallback);

// ❌ 反模式：三元表达式分支挂回调，可读性与可组合性差
builder.setHttpClientConfigCallback(hasCredential
        ? httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(provider)
        : httpClientBuilder -> httpClientBuilder);
```

### 3.2 集合处理用流

节点地址解析属于集合转换，用流处理（`map` / `filter` / `toList`）而非手写 for 循环（见 §2.1 `parseHosts`）。

### 3.3 Bean 生命周期：destroyMethod = "close"

`RestClient` 与 `ElasticsearchClient` 都持有连接资源，**必须**显式声明销毁方法。未声明时容器不会关闭底层连接池，应用停机或多次刷新上下文会残留连接。

```java
@Bean(destroyMethod = "close")
public RestClient elasticsearchRestClient() { ... }

@Bean(destroyMethod = "close")
public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) { ... }
```

> `ElasticsearchTransport` 无需声明销毁方法——它由 `ElasticsearchClient` 的 `close()` 级联关闭。

### 3.4 投影物化：External 版本乐观并发控制

ES 客户端由物化器（`IProjectionMaterializer`）消费，把聚合投影 upsert 到索引。**写模型版本号写入 ES `_version` 元数据**（`VersionType.External`），以此实现跨存储的乐观并发控制与对账：

```java
elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
        .id(projection.getOrderId().toString())
        .versionType(VersionType.External)
        .version(version)          // 写模型副本版本 V
        .document(projection));
```

**为什么用 External 版本**：ES 默认自增 `_version`，无法与写模型版本对齐；显式携带 `version` 后，`_version` 即副本版本 V'，对账机制据此判定副本是否落后。

**配套约定**：

- 索引名等寻址常量集中定义（`OrderEsTargets.ORDER_INDEX_NAME`），写入与读取必须命中同一物理索引。
- 版本解析器（`IReadModelVersionResolver`）读取 `_version` 作为 V'，文档缺失或异常时返回 `-1` 表示副本缺失或不可达。
- 补偿器（`IReadModelResynchronizer`）从写模型重建副本：以聚合旧版本重新物化，覆盖落后或冲突的文档。

### 3.5 失败不静默吞掉，交给对账补偿

物化写失败（含 External 版本冲突，ES 抛 `VersionConflictEngineException`）**不应在物化器内 catch 后静默吞掉**——吞掉会掩盖副本落后，对账机制无从发现。正确做法是让异常向上抛出，交由上层对账补偿（resync / purge）兜底。

```java
// ❌ 反模式：catch 后吞掉，副本落后被掩盖
try {
    elasticsearchClient.index(...);
} catch (RuntimeException | IOException ex) {
    log.warn("ES 物化失败 orderId={}", projection.getOrderId(), ex);
}

// ✅ 推荐：失败上抛，交给对账补偿机制
elasticsearchClient.index(req -> req.index(...)
        .id(projection.getOrderId().toString())
        .versionType(VersionType.External)
        .version(version)
        .document(projection));
```

---

## 4. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 连接参数硬编码在 Java 中 | 密钥泄露、无法按环境切换 | 从 `elasticsearch.*` 外部化配置读取 |
| 引入 Spring Data ES 自动装配 | 客户端不可控、与手写三层冲突 | 用官方 `elasticsearch-java` 客户端显式声明 |
| 三层对象混在一个 Bean 中 | 序列化器 / 传输层无法独立替换 | 按 RestClient → Transport → Client 分层构建 |
| 多节点硬编码单个地址 | 集群容灾缺失 | 逗号分隔 + 流式解析，支持多节点 |
| 条件判断用三元表达式 | 可读性差、无法组合 | 用 `Optional` 表达可选装配 |
| `RestClient` / `Client` 未声明 `destroyMethod` | 连接池泄漏 | 声明 `destroyMethod = "close"` |
| 物化失败 catch 后静默吞掉 | 副本落后被掩盖、对账失效 | 异常上抛，交给对账补偿（resync / purge） |
| 索引名散落在各处硬编码 | 写入读取漂移 | 集中定义于 `*Targets` 常量类 |
| 用 ES 内部自增 `_version` | 无法与写模型版本对齐、对账失真 | 用 `VersionType.External` 写入写模型版本 |

---

## 下一步

- [投影读模型](../core/projection-read.md)：框架 `repository.query` / `repository.reconciliation` 读模型机制详解
- [聚合设计原则](./aggregate-design.md)：聚合根与投影数据来源
- [MySQL 配置设计原则](./mysql-config.md)：对比学习外部化配置与显式装配的通用模式
- [仓储设计原则](./repository-design.md)：写模型与读模型分离
- [Outbox 链路装配](./outbox-config.md)：写模型到异构存储的事件驱动链路
