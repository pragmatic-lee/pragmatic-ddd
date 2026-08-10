# 配置体系

> 本文档介绍框架的配置体系（`io.pragmatic.ddd.config`），包括配置源、类型化绑定与特性开关。

## 1. 概述

框架提供三层配置架构，从底向上逐层语义化：

```
L1  IConfigurationSource     原始键值源（Map / Spring Environment / Nacos / Apollo）
         ↓
L2  ConfigurationBinder       类型化绑定（record / POJO）
         ↓
L3  IFeatureToggle            特性开关（OFF / ROLLOUT / ON + 灰度）

    AbstractConfiguration      门面基类，聚合 L1 + L3，子类编写语义方法
```

设计目标：

- **零框架依赖**：纯 Java 反射实现，不绑定 Spring Environment
- **可适配任意配置后端**：Map / Spring / Nacos / Apollo 均可实现 `IConfigurationSource`
- **类型安全**：通过 `ConfigurationBinder` 绑定为 record / POJO，避免裸字符串
- **特性开关 + 灰度**：内置 OFF / ROLLOUT / ON 三态开关与白名单灰度策略

## 2. L1：配置源 `IConfigurationSource`

`IConfigurationSource` 屏蔽底层配置后端的差异，只暴露原始字符串键值读取：

```java
public interface IConfigurationSource {

    String getString(String key, String defaultValue);

    <T> T get(String key, Class<T> type, T defaultValue);

    boolean contains(String key);

    Set<String> keys();

    default Optional<String> find(String key) { ... }
}
```

### 内置实现 `MapConfigurationSource`

基于内存 Map，可作为测试替身或轻量运行态配置：

```java
MapConfigurationSource source = new MapConfigurationSource()
        .put("rocketmq.name-server", "127.0.0.1:9876")
        .put("rocketmq.producer-group", "ORDER_PRODUCER")
        .put("rocketmq.send-msg-timeout", "5000");

// 读取
String nameServer = source.getString("rocketmq.name-server", "localhost");
int timeout = source.get("rocketmq.send-msg-timeout", Integer.class, 3000);
boolean exists = source.contains("rocketmq.name-server");
```

支持自动转换的类型：`String`、`Integer`、`Long`、`Boolean`、`Double`、`Float`、`Enum`、`Duration`。

::: tip 适配 Spring Environment
在 Spring Boot 项目中，可实现 `IConfigurationSource` 适配 `Environment`：
```java
public class SpringConfigurationSource implements IConfigurationSource {
    private final Environment env;
    // delegate getString / get / contains / keys to env
}
```
:::

## 3. L2：类型化绑定 `ConfigurationBinder`

`ConfigurationBinder` 将某前缀下的键值绑定为类型化对象，零依赖、纯反射：

### 绑定 record（严格模式）

```java
public record RocketMqConfig(
        String nameServer,
        String proxyAddr,
        int retryTimesWhenSendFailed,
        int sendMsgTimeout) { }

// 配置项：rocketmq.name-server / rocketmq.proxy-addr / rocketmq.retry-times-when-send-failed / rocketmq.send-msg-timeout
RocketMqConfig config = ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class);
```

::: tip kebab-case 自动映射
`ConfigurationBinder` 自动将驼峰属性名转换为 kebab-case（小写连字符）。如 `sendMsgTimeout` → `send-msg-timeout`，与配置文件中的短横线风格一致。
:::

### 绑定 record（宽松模式，带默认值）

```java
// 缺失字段取 defaults 同名字段作为兜底值
RocketMqConfig defaults = new RocketMqConfig("localhost", null, 3, 3000);
RocketMqConfig config = ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class, defaults);
```

### 绑定 POJO

```java
// POJO 需有无参构造 + setter
public class MybatisConfig {
    private String dialect = "mysql";
    private int poolSize = 10;

    public void setDialect(String dialect) { this.dialect = dialect; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
}

MybatisConfig config = ConfigurationBinder.bind(source, "mybatis", MybatisConfig.class);
// 缺失字段保留实例默认值
```

### 支持的类型

`ConfigurationBinder` 内置类型转换：

| 类型 | 示例 |
| --- | --- |
| `String` | `"hello"` |
| `int` / `Integer` | `"42"` |
| `long` / `Long` | `"999999"` |
| `boolean` / `Boolean` | `"true"` |
| `double` / `Double` | `"3.14"` |
| `float` / `Float` | `"1.5"` |
| `Enum` | `"CODE"` |
| `Duration` | `"PT5S"` |

### 绑定异常

- 严格模式下，必填字段缺失抛 `ConfigurationBindingException`
- 类型转换失败抛 `ConfigurationBindingException`

## 4. L3：特性开关 `IFeatureToggle`

`IFeatureToggle` 在原始键值之上提供三态开关判定：

```java
public interface IFeatureToggle {

    boolean isEnabled(String featureKey);

    boolean isEnabled(String featureKey, boolean defaultValue);

    boolean isEnabled(String featureKey, FeatureContext context);  // 灰度

    ToggleState stateOf(String featureKey);
}
```

### 三态 `ToggleState`

| 状态 | 配置值 | 语义 |
| --- | --- | --- |
| `OFF` | `OFF` | 关闭，对任何调用返回 `false` |
| `ROLLOUT` | `ROLLOUT` | 灰度，仅命中灰度策略时返回 `true` |
| `ON` | `ON` | 全量开启，对任何调用返回 `true` |

```properties
# 配置示例
feature.new-pricing = ON
feature.beta-api = ROLLOUT
feature.legacy-mode = OFF
```

### 灰度上下文 `FeatureContext`

```java
FeatureContext ctx = new FeatureContext()
        .with("userId", "user-001")
        .with("shopId", "shop-42");

boolean enabled = featureToggle.isEnabled("feature.beta-api", ctx);
```

维度名完全由业务自定义，框架不预设 `userId` / `accountId` 等维度。

### 内置实现 `MapFeatureToggle`

```java
MapFeatureToggle toggle = MapFeatureToggle.from(source);

// 简单判断
boolean on = toggle.isEnabled("feature.new-pricing");

// 灰度判断
FeatureContext ctx = new FeatureContext().with("userId", "user-001");
boolean rollout = toggle.isEnabled("feature.beta-api", ctx);

// 查询状态
ToggleState state = toggle.stateOf("feature.beta-api");  // ROLLOUT
```

### 灰度策略 `IGrayStrategy`

特性处于 `ROLLOUT` 时，由灰度策略决定是否放量：

```java
public interface IGrayStrategy {
    boolean matches(String featureKey, FeatureContext context);
}
```

内置实现 `WhitelistGrayStrategy`，基于白名单按维度分组：

```properties
# 白名单配置
feature.beta-api.allow.userId = user-001,user-002,user-003
feature.beta-api.allow.shopId = shop-42,shop-99
```

判定逻辑：灰度上下文中**任意维度**的取值命中其对应维度的白名单即视为放量。

自定义灰度策略：

```java
public class PercentageGrayStrategy implements IGrayStrategy {

    @Override
    public boolean matches(String featureKey, FeatureContext context) {
        // 按 userId 哈希取模，10% 灰度
        return context.getDimension("userId")
                .map(userId -> Math.abs(userId.hashCode()) % 10 == 0)
                .orElse(false);
    }
}

MapFeatureToggle toggle = new MapFeatureToggle(source, new PercentageGrayStrategy());
```

## 5. 门面基类 `AbstractConfiguration`

`AbstractConfiguration` 聚合 L1 配置源与 L3 特性开关，子类基于聚合维度编写语义方法：

```java
public class OrderConfiguration extends AbstractConfiguration {

    public OrderConfiguration(IConfigurationContext context) {
        super(context);
    }

    // 语义方法：调用方无需感知裸 key
    public String getExternalOrderUrl() {
        return value("order.external.url", "http://default");
    }

    public int getMaxRetryTimes() {
        return value("order.max-retry", Integer.class, 3);
    }

    public boolean isNewPricingEnabled() {
        return feature("feature.new-pricing");
    }

    public boolean isBetaApiEnabled(String userId) {
        FeatureContext ctx = new FeatureContext().with("userId", userId);
        return feature("feature.beta-api", ctx);
    }

    // 类型化绑定
    public ExternalApiConfig getExternalApiConfig() {
        return bind("order.external-api", ExternalApiConfig.class);
    }
}
```

### 配置上下文 `IConfigurationContext`

```java
public interface IConfigurationContext {
    IConfigurationSource source();         // L1 配置源
    IFeatureToggle featureToggle();        // L3 特性开关
}
```

默认实现 `DefaultConfigurationContext`：

```java
// 方式一：从配置源自动派生特性开关
IConfigurationContext context = new DefaultConfigurationContext(source);

// 方式二：自定义特性开关
IConfigurationContext context = new DefaultConfigurationContext(source, customToggle);

// 使用
OrderConfiguration config = new OrderConfiguration(context);
```

## 6. 框架内部使用示例

框架各模块通过 `ConfigurationBinder.bind` 从配置源绑定类型化配置：

```java
// RocketMQ 配置（pragmatic-ddd-rocketmq 模块）
RocketMqConfig config = RocketMqConfig.bind(source);
// 等价于 ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class)

// 本地事件管理器配置（pragmatic-ddd-core 模块）
LocalEventManagerConfig config = LocalEventManagerConfig.bind(source);
// 等价于 ConfigurationBinder.bind(source, "event.local", LocalEventManagerConfig.class, defaultConfig())
```

## 7. 异常体系

```
PragmaticException
 └── ConfigurationBindingException   配置绑定异常
```

- 必填字段缺失 → `ConfigurationBindingException("缺少必需的配置项[key]")`
- 类型转换失败 → `ConfigurationBindingException("配置项[key]无法转换为类型[type]")`

---

下一步：

- [防腐层（ACL）](./acl.md)
- [配置项参考](../reference/configuration.md)
- [RocketMQ 集成](../integration/rocketmq.md)
