# 外部依赖声明（Dependency）

> 本文档介绍聚合的外部依赖声明（`io.pragmatic.ddd.dependency`）的使用：如何在领域层描述"本聚合依赖了哪些外部聚合 / 系统"。
> 关联阅读：[防腐层（ACL）](./acl.md)——防腐层负责"怎么调用外部"，本文负责"依赖了什么"。

## 1. 概述

外部依赖声明用于**在领域层表达聚合对外部世界的契约**：本聚合需要从哪些外部实体（其他聚合或外部系统）获取数据或能力。

它与防腐层（ACL）正交：

- **依赖声明（本文）**：描述"依赖了什么"，是领域层的元数据 / 契约，无调用行为。
- **防腐层（ACL）**：描述"怎么调用外部"，是基础设施层的封装机制（转换、通信、异常分类、日志）。

典型关系：领域层定义 `I{目标}Dependency` 接口并标注 `@ExternalDependency`（本文），由基础设施层的防腐适配器（ACL）实现该接口。

## 2. 核心类型

| 类型 | 说明 |
| --- | --- |
| `@ExternalDependency` | 注解，标记接口为本聚合的外部依赖声明 |
| `DependencyType` | 枚举，区分依赖类型 |
| `IDependency` | 标记接口，供框架识别业务侧定义的外部依赖接口 |

## 3. 声明聚合的外部依赖

在领域层定义接口并标注：

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

| 注解属性 | 说明 |
| --- | --- |
| `targetName` | 依赖的目标实体名称（聚合名或系统名） |
| `type` | 依赖类型：`AGGREGATE`（同系统其他聚合）或 `EXTERNAL_SYSTEM`（系统外服务） |
| `description` | 业务描述 |

### 依赖类型

| 枚举值 | 语义 |
| --- | --- |
| `AGGREGATE` | 外部聚合：同一系统内的其他聚合根（默认值） |
| `EXTERNAL_SYSTEM` | 外部系统：系统边界之外的服务、第三方 API |

## 4. 设计要点

- **依赖倒置**：领域层只定义接口（契约），不感知远程调用与转换细节；具体调用由防腐适配器实现。
- **分层归属**：声明属于**领域层**，可在领域层直接引用；防腐机制属于**基础设施层**。
- **运行期不强制校验**：`@ExternalDependency` 用于可视化与架构分析，标识聚合间的依赖关系，运行期不强制校验。

::: tip 用途
`@ExternalDependency` 用于可视化与架构分析，标识聚合间的依赖关系。运行期不强制校验。
:::

## 5. 与防腐层的协作

领域层声明契约，基础设施层用 ACL 调用器实现契约：

```
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
