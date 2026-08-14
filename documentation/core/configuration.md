# 配置体系（Configuration）

> 本文档说明 `io.pragmatic.ddd.config` 包提供的配置能力：配置源、类型化绑定与特性开关。相关文档：[配置项参考](../reference/configuration.md) · [RocketMQ 集成](../integration/rocketmq.md)。

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.config` 提供三层配置架构，从底向上逐层语义化，使业务代码以**类型安全**方式读取配置、以**业务语义**判定特性开关，而不直接耦合具体配置后端（Map / Spring Environment / Nacos / Apollo）。

设计目标：

- **零框架依赖**：纯 Java 反射实现，不绑定 Spring Environment。
- **可适配任意配置后端**：Map / Spring / Nacos / Apollo 均可实现 `IConfigurationSource`。
- **类型安全**：通过 `ConfigurationBinder` 绑定为 record / POJO，避免裸字符串。
- **特性开关 + 灰度**：内置 OFF / ROLLOUT / ON 三态开关与白名单灰度策略。

### 1.2 概念层级与依赖关系

```text
L1  IConfigurationSource       原始键值源（Map / Spring / Nacos / Apollo）
         ↓
L2  ConfigurationBinder        类型化绑定（record / POJO）
         ↓
L3  IFeatureToggle             特性开关（OFF / ROLLOUT / ON + 灰度）
         └─ IGrayStrategy      灰度策略 SPI（matches 判定放量）

    IConfigurationContext      聚合 L1 + L3 的上下文
         ↓
    AbstractConfiguration      门面基类，子类编写语义方法
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IConfigurationSource` | `io.pragmatic.ddd.config` | L1 原始键值源，屏蔽后端差异 |
| `MapConfigurationSource` | `io.pragmatic.ddd.config` | 基于内存 Map 的内置实现 / 测试替身 |
| `ConfigurationBinder` | `io.pragmatic.ddd.config` | L2 类型化绑定（record / POJO） |
| `IFeatureToggle` | `io.pragmatic.ddd.config.feature` | L3 特性开关判定 |
| `ToggleState` | `io.pragmatic.ddd.config.feature` | 三态枚举（OFF / ROLLOUT / ON） |
| `FeatureContext` | `io.pragmatic.ddd.config.feature` | 灰度维度上下文 |
| `IGrayStrategy` / `WhitelistGrayStrategy` | `io.pragmatic.ddd.config.feature` | 灰度策略 SPI 与白名单内置实现 |
| `IConfigurationContext` / `DefaultConfigurationContext` | `io.pragmatic.ddd.config.context` | 配置上下文 |
| `AbstractConfiguration` | `io.pragmatic.ddd.config` | 门面基类 |

## 2. 核心概念详解

### 2.1 配置源：`IConfigurationSource`

屏蔽底层配置后端差异，只暴露原始字符串键值读取。

```java
public interface IConfigurationSource {

    String getString(String key, String defaultValue);

    <T> T get(String key, Class<T> type, T defaultValue);

    boolean contains(String key);

    Set<String> keys();

    default Optional<String> find(String key) { ... }
}
```

支持自动转换的类型（`get` 与绑定通用）：`String`、`Integer`、`Long`、`Boolean`、`Double`、`Float`、`Enum`、`Duration`。

#### 内置实现：`MapConfigurationSource`

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

> 适配 Spring Environment：在 Spring Boot 项目中实现 `IConfigurationSource` 委托给 `Environment` 的 `getProperty` 即可，框架不强制耦合 Spring。

### 2.2 类型化绑定：`ConfigurationBinder`

将某前缀下的键值绑定为类型化对象，零依赖、纯反射。

#### 绑定 record（严格模式）

`defaults` 传 `null` 时，record 字段缺失即视为必填。键名按 `{prefix}.{kebab-case 组件名}` 读取。

```java
public record RocketMqConfig(
        String nameServer,
        String proxyAddr,
        int retryTimesWhenSendFailed,
        int sendMsgTimeout) { }

// 配置项：rocketmq.name-server / rocketmq.proxy-addr / rocketmq.retry-times-when-send-failed / rocketmq.send-msg-timeout
RocketMqConfig config = ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class);
```

> kebab-case 自动映射：`ConfigurationBinder` 将驼峰属性名转为 kebab-case（小写连字符），如 `sendMsgTimeout` → `send-msg-timeout`，与配置文件短横线风格一致。

#### 绑定 record（宽松模式，带默认值）

`defaults` 非 `null` 时，缺失字段取 `defaults` 同名字段作为兜底值：

```java
RocketMqConfig defaults = new RocketMqConfig("localhost", null, 3, 3000);
RocketMqConfig config = ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class, defaults);
```

#### 绑定 POJO

POJO 需有无参构造 + setter；缺失字段跳过 setter、保留实例默认值：

```java
public class MybatisConfig {
    private String dialect = "mysql";
    private int poolSize = 10;

    public void setDialect(String dialect) { this.dialect = dialect; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
}

MybatisConfig config = ConfigurationBinder.bind(source, "mybatis", MybatisConfig.class);
```

#### 支持的类型

| 类型 | 示例 |
| --- | --- |
| `String` | `"hello"` |
| `int` / `Integer` | `"42"` |
| `long` / `Long` | `"999999"` |
| `boolean` / `Boolean` | `"true"` |
| `double` / `Double` | `"3.14"` |
| `float` / `Float` | `"1.5"` |
| `Enum` | `"CODE"`（`Enum.valueOf`） |
| `Duration` | `"PT5S"`（`Duration.parse`） |

### 2.3 特性开关：`IFeatureToggle`

在原始键值之上提供三态开关判定，`feature.{key} = OFF / ROLLOUT / ON` 决定状态。

```java
public interface IFeatureToggle {

    boolean isEnabled(String featureKey);

    boolean isEnabled(String featureKey, boolean defaultValue);

    boolean isEnabled(String featureKey, FeatureContext context);  // 灰度

    ToggleState stateOf(String featureKey);
}
```

#### 三态：`ToggleState`

| 状态 | 配置值 | 语义 |
| --- | --- | --- |
| `OFF` | `OFF` | 关闭，对任何调用返回 `false` |
| `ROLLOUT` | `ROLLOUT` | 灰度，仅命中灰度策略时返回 `true` |
| `ON` | `ON` | 全量开启，对任何调用返回 `true` |

```properties
feature.new-pricing = ON
feature.beta-api = ROLLOUT
feature.legacy-mode = OFF
```

#### 灰度上下文：`FeatureContext`

维度名完全由业务自定义，框架不预设 `userId` / `accountId` 等维度。

```java
FeatureContext ctx = new FeatureContext()
        .with("userId", "user-001")
        .with("shopId", "shop-42");

boolean enabled = featureToggle.isEnabled("feature.beta-api", ctx);
```

#### 灰度策略：`IGrayStrategy` 与 `WhitelistGrayStrategy`

特性处于 `ROLLOUT` 时，由灰度策略决定是否放量。

```java
public interface IGrayStrategy {
    boolean matches(String featureKey, FeatureContext context);
}
```

内置 `WhitelistGrayStrategy` 基于白名单按维度分组：

```properties
# 白名单配置：{featureKey}.allow.{dimension}=值1,值2,...
feature.beta-api.allow.userId = user-001,user-002,user-003
feature.beta-api.allow.shopId = shop-42,shop-99
```

判定逻辑：灰度上下文中**任意维度**的取值命中其对应维度的白名单即视为放量。

自定义灰度策略示例（按 `userId` 哈希取模 10% 灰度）：

```java
public class PercentageGrayStrategy implements IGrayStrategy {

    @Override
    public boolean matches(String featureKey, FeatureContext context) {
        return context.getDimension("userId")
                .map(userId -> Math.abs(userId.hashCode()) % 10 == 0)
                .orElse(false);
    }
}
```

`WhitelistGrayStrategy` 构造需传入 `IConfigurationSource`；若使用自定义策略，将其传给 `IFeatureToggle` 实现以参与 `ROLLOUT` 判定。

### 2.4 门面基类：`AbstractConfiguration`

聚合 L1 配置源与 L3 特性开关，子类基于聚合维度编写语义方法，调用方无需感知裸 key。

```java
public class OrderConfiguration extends AbstractConfiguration {

    public OrderConfiguration(IConfigurationContext context) {
        super(context);
    }

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

    public ExternalApiConfig getExternalApiConfig() {
        return bind("order.external-api", ExternalApiConfig.class);
    }
}
```

受保护 helper：

| 方法 | 说明 |
| --- | --- |
| `value(String, String)` / `value(String, Class<T>, T)` | 经 L1 读取原始 / 类型化值 |
| `feature(String)` / `feature(String, FeatureContext)` | 经 L3 判定开关 |
| `bind(String, Class<T>)` | 经 `ConfigurationBinder` 绑定前缀下配置 |
| `source()` / `featureToggle()` / `context()` | 暴露底层组件（高级用法） |

#### 配置上下文：`IConfigurationContext`

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

OrderConfiguration config = new OrderConfiguration(context);
```

## 3. 关键机制与避坑指南

### 3.1 类型化绑定机制

- record 按组件名构造，键名 `{prefix}.{kebab-case 组件名}`；POJO 按 setter 属性名注入，键名 `{prefix}.{kebab-case 属性名}`。
- 严格模式（`defaults = null`）下 record 字段缺失 → 抛 `ConfigurationBindingException`。
- 宽松模式（`defaults != null`）下 record 缺失字段取 `defaults` 同名字段；POJO 缺失字段跳过 setter、保留实例默认值。
- 类型转换失败（如 `"abc"` → `Integer`）→ 抛 `ConfigurationBindingException`。

### 3.2 特性开关与灰度判定

- `OFF` 对所有调用返回 `false`；`ON` 对所有调用返回 `true`；`ROLLOUT` 仅当 `IGrayStrategy.matches(...)` 为真时返回 `true`。
- `isEnabled(featureKey, boolean defaultValue)`：特性键未配置时返回 `defaultValue`。
- `WhitelistGrayStrategy` 的判定规则为**任意维度命中即放量**（OR 语义），非全部维度命中。
- 灰度维度名完全由业务决定；配置键固定为 `{featureKey}.allow.{dimension}`。

### 3.3 框架内部使用示例

框架各模块通过 `ConfigurationBinder.bind` 从配置源绑定类型化配置：

```java
// RocketMQ 配置（pragmatic-ddd-rocketmq 模块）
RocketMqConfig config = RocketMqConfig.bind(source);
// 等价于 ConfigurationBinder.bind(source, "rocketmq", RocketMqConfig.class)

// 本地事件管理器配置（pragmatic-ddd-core 模块）
LocalEventManagerConfig config = LocalEventManagerConfig.bind(source);
// 等价于 ConfigurationBinder.bind(source, "event.local", LocalEventManagerConfig.class, defaultConfig())
```

> **重要约束**：`AbstractConfiguration` 为抽象基类，子类**必须**提供 `protected` 构造并调用 `super(IConfigurationContext)`；其 `value` / `feature` / `bind` 等 helper 均为 `protected`，仅子类语义方法内可用，调用方经子类语义方法读取，不直接接触裸 key。

## 4. 异常与错误处理体系

### 4.1 继承关系

```text
RuntimeException
 └─ PragmaticException
      └─ ConfigurationBindingException   配置绑定异常
```

### 4.2 异常触发场景

| 触发场景 | 异常信息 |
| --- | --- |
| 严格模式必填字段缺失 | `ConfigurationBindingException("缺少必需的配置项[{key}]（绑定字段：{field}）")` |
| 类型转换失败 | `ConfigurationBindingException("配置项[{key}]无法转换为类型[{type}]")` |
| record / POJO 反射构造失败 | `ConfigurationBindingException("绑定 record[{type}]失败" / "绑定 POJO[{type}]失败")` |

### 4.3 捕获与映射规范

- 统一兜底：`catch (PragmaticException e)` 可捕获配置绑定异常。
- 绑定异常为配置期错误，应在应用启动 / 配置加载阶段暴露，避免运行期因缺失配置静默降级。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 配置源 | 实现 `IConfigurationSource`，或用 `MapConfigurationSource` | 屏蔽后端差异；支持 String/Integer/Long/Boolean/Double/Float/Enum/Duration |
| 类型化绑定 | `ConfigurationBinder.bind(source, prefix, type[, defaults])` | record 严格模式字段缺失抛异常；键名 kebab-case 映射 |
| 特性开关 | `IFeatureToggle.isEnabled(key[, ctx])` | OFF/ON/ROLLOUT 三态；ROLLOUT 依赖灰度策略 |
| 灰度策略 | `WhitelistGrayStrategy(source)` 或自定义 `IGrayStrategy` | 白名单 OR 语义；维度名业务自定义 |
| 门面配置 | 继承 `AbstractConfiguration`，`super(context)` | helper 为 `protected`，调用方经语义方法读取 |
| 配置上下文 | `DefaultConfigurationContext(source[, toggle])` | 聚合 L1 + L3 |
| 异常 | `ConfigurationBindingException` | 启动期暴露，勿静默降级 |

## 6. 命名规范速查

结合框架事实约束（接口以 `I` 开头、配置键 kebab-case、特性键 `feature.` 前缀、白名单键 `{featureKey}.allow.{dimension}`），约定如下：

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 配置源接口 | `I{后端}ConfigurationSource` | `ISpringConfigurationSource` |
| 内置配置源类 | `{后端}ConfigurationSource` | `MapConfigurationSource` |
| 绑定配置类（record/POJO） | `{模块}Config` / `{模块}Configuration` | `RocketMqConfig`、`MybatisConfig` |
| 配置键 | kebab-case，小写下划线连字符，按模块前缀分组 | `rocketmq.name-server`、`order.max-retry` |
| 特性键 | `feature.{语义}` | `feature.new-pricing` |
| 白名单键 | `{featureKey}.allow.{dimension}` | `feature.beta-api.allow.userId` |
| 特性开关接口 | `I{语义}FeatureToggle` / 通用 `IFeatureToggle` | `IFeatureToggle` |
| 灰度策略接口 / 实现 | `I{策略}GrayStrategy` / `{策略}GrayStrategy` | `IGrayStrategy`、`WhitelistGrayStrategy` |
| 门面配置子类 | `{聚合/模块}Configuration`（继承 `AbstractConfiguration`） | `OrderConfiguration` |
| 语义读取方法 | `get{语义}` / `is{语义}Enabled` | `getExternalOrderUrl`、`isNewPricingEnabled` |

> ⚠️ **重要约束**：配置键一律使用 kebab-case（`rocketmq.name-server`），与 `ConfigurationBinder` 的驼峰→短横线自动映射保持一致；若手写键名使用驼峰（如 `nameServer`），绑定将因找不到对应 kebab-case 键而触发必填缺失异常。

**下一步阅读**

- [配置项参考](../reference/configuration.md)：各模块完整配置项清单
- [防腐层（ACL）](./acl.md)：外部调用封装
- [RocketMQ 集成](../integration/rocketmq.md)：配置实战
