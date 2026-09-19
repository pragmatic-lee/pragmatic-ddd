# 外部依赖声明落地模式

> 本文档介绍 Pragmatic DDD 中外部依赖声明（`@ExternalDependency` / `IDependency` / `DependencyType`）的落地方式：领域层如何声明「依赖了哪个外部目标」、防腐适配器（ACL）如何实现、应用层如何消费，以及约束与反模式。前置阅读：[核心：外部依赖声明](../core/dependency.md)。

## 1. 本质与定位

外部依赖声明（`io.pragmatic.ddd.dependency`）是**领域层的元数据声明**：描述「本聚合依赖了哪个外部目标（聚合 / 系统）」，而**不负责真正的远程调用**。它与 `@DomainService` 对称——一个声明「我需要什么能力」，一个声明「我依赖什么外部聚合 / 系统」（见 [领域服务落地模式](./domain-service.md)）。

真正的调用、协议转换、异常分类由**防腐适配器（ACL）**在基础设施层实现，二者职责不可混用：

| 维度 | 外部依赖声明（领域层） | 防腐适配器（基础设施层） |
| --- | --- | --- |
| 形态 | `@ExternalDependency` + `IDependency` 接口 | `@Component` 实现领域端口 |
| 职责 | 声明「依赖了什么」 | 封装「怎么调用外部」 |
| 是否远程调用 | 否 | 是 |

> 声明即血缘：即便运行期不强制校验，声明仍是架构可视化 / 依赖分析的唯一来源，遵循「先定义后实现」——即使目标聚合 / 系统尚未就绪，也先声明端口，适配器打桩。

## 2. 命名与包结构

### 2.1 包结构

```text
domain/order/
└── dependency/   外部依赖端口声明（IUserDependency 等，继承 IDependency 并标 @ExternalDependency）

infrastructure/order/
└── dependency/   防腐适配器实现（UserGatewayAdapter 等，@Component 实现领域端口）
```

### 2.2 命名规范

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 依赖声明接口 | `I{目标}Dependency`（继承 `IDependency`，标 `@ExternalDependency`） | `IUserDependency` |
| 外部系统依赖接口 | `I{目标系统}ClientDependency`（`type = EXTERNAL_SYSTEM`） | `IPaymentGatewayDependency` |
| `targetName` | 小写下划线 / 连字符，全局唯一，与适配器指向的外部目标一致 | `user`、`payment-gateway` |
| 防腐适配器实现类 | `{目标}GatewayAdapter` / `{目标}ClientAdapter`（`@Component`，基础设施层） | `UserGatewayAdapter` |

> 接口名必须体现业务意图；接口名以 `I` 开头，供架构扫描统一识别。

## 3. 数据 / 职责承载

| 承载 | 不承载 |
| --- | --- |
| 声明元数据（`targetName` / `type` / `description`） | 远程调用、协议转换、异常分类（归 ACL） |
| 端口接口方法签名（声明「能取什么 / 能做什么」） | 运行期可用性保证（声明不强制校验） |
| 业务意图描述（`description` 解释依赖目的） | 领域模型对外部状态的持久化 |

> 端口由应用层服务 / 事件订阅者通过构造注入消费；领域模型本身不持有远程状态、不持久化外部返回值。

## 4. 落地方式（核心）

以下以 `Order` 聚合为例演示**示例写法**。落地时请把 `Order` / `User` / `PointsService` 等替换为你自己的聚合名与外部目标——命名规范（§2.2）与反模式（§6）均为聚合无关规则，可原样套用。

### 4.1 领域层声明端口

在 `domain/order/dependency/` 下定义接口，继承 `IDependency` 并标注 `@ExternalDependency`。`targetName` 必填且全局唯一；`type` 默认 `AGGREGATE`，跨系统边界显式声明 `EXTERNAL_SYSTEM`：

```java
package io.pragmatic.ddd.example.order.domain.order.dependency;

@ExternalDependency(
        targetName = "User",
        type = DependencyType.AGGREGATE,
        description = "用户聚合：提供用户等级以决定订单金额折扣，并提供用户是否生效且具备下单资格的判定"
)
public interface IUserDependency extends IDependency {

    int getUserLevel(String userId);

    String getUserMobile(String userId);

    boolean isUserQualified(String userId);
}
```

### 4.2 防腐适配器实现（基础设施层）

在 `infrastructure/dependency/order/` 用 `@Component` 实现领域端口，封装「怎么调用外部」。目标未就绪时先打桩，运行期不强制校验：

```java
package io.pragmatic.ddd.example.order.infrastructure.dependency.order;

@Component
public class UserGatewayAdapter implements IUserDependency {

    @Override
    public int getUserLevel(String userId) {
        return 0;                       // 打桩：真实场景查询用户聚合 / 用户服务
    }

    @Override
    public String getUserMobile(String userId) {
        return "13800000000";           // 打桩
    }

    @Override
    public boolean isUserQualified(String userId) {
        return true;                    // 打桩
    }
}
```

### 4.3 应用层消费端口

应用层服务构造注入端口，**领域模型不感知远程调用**。下例用用户等级计算折扣：

```java
@Service
public class OrderTotalAmountCalculator implements IOrderTotalAmountCalculator {

    private final IUserDependency userDependency;

    public OrderTotalAmountCalculator(IUserDependency userDependency) {
        this.userDependency = userDependency;
    }

    @Override
    public Money calculate(List<OrderItem> items, Order entity) {
        int level = userDependency.getUserLevel(String.valueOf(entity.getCustomer().getCustomerId()));
        BigDecimal rate = discountRateOf(level);          // 等级→折扣率映射内聚于此
        return items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.ZERO, Money::add)
                .multiply(rate);
    }
}
```

### 4.4 外部系统写依赖 + 幂等

对**写外部**（如发放积分），声明端口后，适配器继承 `AbstractIdempotentWriteGateway`，以业务幂等键（`bizId`）保证重复事件不重复发放：

```java
@ExternalDependency(targetName = "PointsService", type = DependencyType.EXTERNAL_SYSTEM,
        description = "积分服务：按业务幂等键为指定用户增加积分")
public interface IUserPointsDependency extends IDependency {
    void increasePoints(IncreasePointsCommand command);   // command 携带 bizId 幂等键
}
```

```java
@Component
public class UserPointsDependencyAdapter
        extends AbstractIdempotentWriteGateway<IncreasePointsCommand, Void, PointsRequest, PointsResponse, String>
        implements IUserPointsDependency {

    @Override
    public void increasePoints(IncreasePointsCommand command) {
        write(command);                                    // 先查后写，bizId 命中则短路
    }

    @Override
    protected String uniqueKey(IncreasePointsCommand param) {
        return param.bizId();
    }
    // toExternalRequest / doWrite / queryByKey 等钩子封装真实调用与幂等查询
}
```

### 4.5 装配

端口与适配器均交由 Spring 管理：适配器 `@Component` 后即可被应用层按类型注入，无需额外 `@Bean`；`@ExternalDependency` 仅作元数据，不参与运行期装配。

## 5. 关键机制与避坑

- **`targetName` 必填、全局唯一**：是依赖关系的唯一标识，须与防腐适配器指向的外部目标一致（见 [core/dependency.md §2.1/§6](../core/dependency.md)）。声明叫 `inventory`、适配器指向 `stock`，架构血缘 / 可视化无法关联。
- **运行期不强制校验**：声明未实现 / 不可用也不会报错，不能当作「可用性保证」；端口注入缺失由容器负责（见 [core/dependency.md §3.3](../core/dependency.md)）。
- **仅标记「依赖了什么」**：远程调用、协议转换、异常分类归 ACL，禁止塞进依赖接口（见 [core/dependency.md §3.3](../core/dependency.md)）。
- **接口以 `I` 开头、继承 `IDependency`**：供架构扫描统一识别。
- **`type` 默认 `AGGREGATE`**：跨系统边界显式声明 `EXTERNAL_SYSTEM`（见 [core/dependency.md §2.3](../core/dependency.md)）。
- **端口消费在应用层**：领域模型不持久化外部返回值；外部读不入本聚合事务。外部调用经事件订阅者触发（见 [事件订阅领域服务落地模式](./event-subscriber-pattern.md)），不进本聚合数据库事务，避免外部不可用导致本地事务回滚风暴。

> ⚠️ **`targetName` 不一致是高频坑**：声明与实现用同一全局唯一 `targetName`，否则依赖可视化 / 架构分析无法把声明与实现关联起来。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 领域层直接写 HTTP / RPC 调用 | 违反分层与依赖倒置，领域模型耦合远程细节 | 定义 `I{目标}Dependency` 端口 + 基础设施 ACL 适配器 |
| `targetName` 命名与适配器目标不一致 | 架构血缘 / 可视化关联失败 | 声明与实现用同一全局唯一 `targetName` |
| 把 `@ExternalDependency` 当运行时可用性保证 | 实则不校验，误以为缺依赖会被拦截 | 端口注入缺失由容器负责，声明只管血缘 |
| 把调用行为 / 异常分类写进依赖接口 | 声明与调用职责混用 | 接口只定义方法签名；实现侧封装转换与 `AclExceptions` |
| 只写 ACL 适配器不声明 | 失去架构血缘与依赖分析 | 先定义 `I{目标}Dependency` 端口，再实现适配器 |
| 一个接口塞多个目标 / 多个外部系统 | 血缘无法细分、难以替换 | 一目标一端口（一接口一职责） |
| 写外部不幂等 | 重复事件导致积分重复发放等 | 继承 `AbstractIdempotentWriteGateway`，以 `bizId` 先查后写 |

## 7. 下一步

- [领域服务落地模式](./domain-service.md)：`@DomainService` 与 `@ExternalDependency` 对称——一个声明能力，一个声明外部依赖
- [事件订阅领域服务落地模式](./event-subscriber-pattern.md)：订阅者如何经领域端口（`IDependency`）触发外部写
- [聚合设计原则](./aggregate-design.md)：聚合边界与 ID 引用
- [核心：外部依赖声明](../core/dependency.md)：`@ExternalDependency` / `IDependency` / `DependencyType` 全量机制与约束
- [核心：防腐层（ACL）](../core/acl.md)：`AbstractQueryGateway` / `AbstractWriteGateway` / `AbstractIdempotentWriteGateway` / `ExternalCall` / `AclExceptions`
- [MyBatis 集成](../integration/mybatis.md)：端口与适配器的装配
