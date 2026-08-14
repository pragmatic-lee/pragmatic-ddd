# 外部依赖声明（Dependency）

> 本文档说明 `io.pragmatic.ddd.dependency` 包提供的外部依赖声明能力。相关文档：[防腐层（ACL）](./acl.md) —— 防腐层负责"怎么调用外部"，本文负责"依赖了什么"。

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.dependency` 提供在**领域层**表达聚合对外部世界契约的能力：本聚合需要从哪些外部实体（其他聚合或外部系统）获取数据或能力。它通过 `@ExternalDependency` 注解标记接口、以 `IDependency` 标记接口供框架识别，并将依赖类型以 `DependencyType` 枚举区分。

它与防腐层（ACL）正交：

- **依赖声明（本文）**：描述"依赖了什么"，是领域层的元数据 / 契约，无调用行为。
- **防腐层（ACL）**：描述"怎么调用外部"，是基础设施层的封装机制（转换、通信、异常分类、日志）。

典型关系：领域层定义 `I{目标}Dependency` 接口并标注 `@ExternalDependency`（本文），由基础设施层的防腐适配器（ACL）实现该接口。

### 1.2 概念层级与依赖关系

```text
@ExternalDependency        注解（标记接口为外部依赖声明）
  └─ IDependency           标记接口（供框架识别业务侧依赖接口）

DependencyType             枚举（AGGREGATE / EXTERNAL_SYSTEM）
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `@ExternalDependency` | `io.pragmatic.ddd.dependency` | 注解，标记接口为本聚合的外部依赖声明 |
| `IDependency` | `io.pragmatic.ddd.dependency` | 标记接口，供框架识别业务侧定义的外部依赖接口 |
| `DependencyType` | `io.pragmatic.ddd.dependency` | 枚举，区分依赖类型 |

## 2. 核心概念详解

### 2.1 标记注解：`@ExternalDependency`

作用于接口（`ElementType.TYPE`），运行时保留（`RetentionPolicy.RUNTIME`），用于声明"本聚合依赖了某个外部目标"。

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExternalDependency {
    String targetName();                       // 依赖的目标实体名称（聚合名或系统名）
    DependencyType type() default DependencyType.AGGREGATE;
    String description() default "";
}
```

| 注解属性 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `targetName` | 是 | 无 | 依赖的目标实体名称（聚合名或系统名） |
| `type` | 否 | `DependencyType.AGGREGATE` | 依赖类型 |
| `description` | 否 | `""` | 业务描述 |

#### 示例代码

```java
@ExternalDependency(
        targetName = "inventory",
        type = DependencyType.AGGREGATE,
        description = "库存聚合"
)
public interface InventoryDependency extends IDependency {

    StockInfo getStock(String skuId);
}
```

### 2.2 标记接口：`IDependency`

```java
public interface IDependency { }   // 纯标记，供框架识别业务侧定义的外部依赖接口
```

业务侧所有外部依赖声明接口应继承 `IDependency`，使框架（如架构扫描、依赖可视化）能统一识别，不承载行为契约。

### 2.3 依赖类型：`DependencyType`

```java
public enum DependencyType {
    AGGREGATE,          // 外部聚合：同一系统内的其他聚合根
    EXTERNAL_SYSTEM     // 外部系统：系统边界之外的服务、第三方 API
}
```

| 枚举值 | 语义 |
| --- | --- |
| `AGGREGATE` | 外部聚合：同一系统内的其他聚合根（默认值） |
| `EXTERNAL_SYSTEM` | 外部系统：系统边界之外的服务、第三方 API |

## 3. 关键机制与避坑指南

### 3.1 依赖声明与防腐层协作

领域层声明契约，基础设施层用 ACL 适配器实现契约：

```text
领域层（dependency）                  基础设施层（acl）
┌─────────────────────┐             ┌──────────────────────────┐
│ IUserDependency      │             │ UserGatewayAdapter        │
│  @ExternalDependency │ ──实现──>    │  implements IUserDependency │
│  (依赖声明)           │             │  = ExternalCall.query(...) │
└─────────────────────┘             └──────────────────────────┘
       依赖了什么                        怎么调用外部
```

- 一个聚合可以只声明依赖、由本地仓储实现（无需 ACL）。
- 也可以写 ACL 适配器但不加 `@ExternalDependency` 注解。

### 3.2 设计要点

- **依赖倒置**：领域层只定义接口（契约），不感知远程调用与转换细节；具体调用由防腐适配器实现。
- **分层归属**：声明属于**领域层**，可在领域层直接引用；防腐机制属于**基础设施层**。

### 3.3 运行期行为约束

> **重要约束**：`@ExternalDependency` 用于可视化与架构分析，标识聚合间的依赖关系，运行期**不强制校验**。即：即便某外部依赖声明未被实现或实现不可用，框架也不会在运行期因缺少声明而报错。

> **重要约束**：`@ExternalDependency` 仅标记"依赖了什么"，不承载任何调用行为。真正的远程调用、协议转换与异常分类由防腐层（ACL）负责，二者职责不可混用。

## 4. 异常与错误处理体系

外部依赖声明本身**不定义专属异常类型**，也不在运行期强制校验依赖的可用性。依赖调用过程中出现的通信、转换、超时等异常由防腐层（ACL）负责分类与处理，详见 [防腐层（ACL）](./acl.md)。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 依赖声明 | 接口继承 `IDependency` 并标注 `@ExternalDependency` | `targetName` 必填；运行期不强制校验，仅用于可视化 / 架构分析 |
| 标记接口 | 所有外部依赖接口继承 `IDependency` | 纯标记，供框架识别，不承载行为 |
| 依赖类型 | `type = DependencyType.AGGREGATE / EXTERNAL_SYSTEM` | 默认 `AGGREGATE` |
| 与 ACL 关系 | 声明契约，由防腐适配器实现 | 声明管"依赖什么"，ACL 管"怎么调用"，职责正交 |

**下一步阅读**

- [防腐层（ACL）](./acl.md)：外部调用的封装、转换与异常分类
- [领域建模](./domain-modeling.md)：实体 / 值对象 / 聚合根基础建模能力

## 6. 命名规范速查

结合框架事实约束（依赖声明接口以 `I` 开头、继承 `IDependency` 并标注 `@ExternalDependency`），约定如下：

| 元素 | 格式 | 示例 |
|------|------|------|
| 依赖声明接口 | `I{目标}Dependency`（继承 `IDependency`，标 `@ExternalDependency`） | `IInventoryDependency` |
| 外部系统依赖接口 | `I{目标系统}ClientDependency`（强调系统边界外） | `IInventoryClientDependency` |
| `targetName` 属性 | 小写下划线 / 连字符的聚合名或系统名，全局唯一标识 | `inventory`、`payment-gateway` |
| `description` 属性 | 中文业务描述，说明"依赖了什么" | `库存聚合` |
| 防腐适配器实现类 | `{目标}GatewayAdapter` / `{目标}ClientAdapter`（实现依赖接口，属基础设施层） | `InventoryGatewayAdapter` |

> ⚠️ **重要约束**：`targetName` 是依赖关系的唯一标识，应与防腐层适配器所指向的外部目标一致；命名不一致会导致依赖可视化 / 架构分析无法正确关联声明与实现。
