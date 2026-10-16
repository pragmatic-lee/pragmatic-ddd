# BP 卡：外部依赖声明落地模式（dependency-declaration）

> 源：`documentation/best-practices/dependency-declaration.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 包结构

```text
domain/order/dependency/
└── I{目标}Dependency.java        @ExternalDependency + extends IDependency（领域层声明）
infrastructure/order/dependency/
└── {目标}GatewayAdapter.java     @Component implements 领域端口（ACL 实现）
```

## 2. 命名规范

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 依赖声明接口 | `I{目标}Dependency extends IDependency`，标 `@ExternalDependency` | `IUserDependency` |
| 外部系统依赖 | `I{目标系统}ClientDependency`（`type = EXTERNAL_SYSTEM`） | `IPaymentGatewayDependency` |
| `targetName` | 小写下划线/连字符，全局唯一，与适配器指向目标一致 | `user`、`payment-gateway` |
| 防腐适配器 | `{目标}GatewayAdapter`/`{目标}ClientAdapter`（`@Component`） | `UserGatewayAdapter` |

> 包路径（A7 已核实）：`@ExternalDependency` / `IDependency` / `DependencyType` → `io.pragmatic.ddd.dependency`（即 `io.pragmatic.ddd.dependency.ExternalDependency` / `io.pragmatic.ddd.dependency.IDependency` / `io.pragmatic.ddd.dependency.DependencyType`）；写外部幂等基类 `AbstractIdempotentWriteGateway` → `io.pragmatic.ddd.acl.AbstractIdempotentWriteGateway`。

## 3. 关键约定

- 外部依赖声明是**领域层元数据**：描述「依赖了什么外部目标」，不负责远程调用；与 `@DomainService` 对称。
- 真正调用/协议转换/异常分类由基础设施层 ACL 实现，二者职责不可混用。
- `targetName` 必填、全局唯一，且声明与适配器必须一致（否则依赖可视化/血缘无法关联）。
- `type` 默认 `AGGREGATE`；跨系统边界显式声明 `EXTERNAL_SYSTEM`。
- 运行期不强制校验声明可用性；端口注入缺失由容器负责。声明即血缘，遵循「先定义后实现」（目标未就绪先打桩）。
- 端口在应用层/事件订阅者经构造注入消费；领域模型不持有远程状态、不承载外部契约对象（对方 Response/DTO/SDK 对象）。
- 外部返回值经 ACL 响应转换产出领域值后（单值标识符用基本类型/`String`，复合结构用值对象），**可作为聚合字段持久化**；被禁的是外部契约对象，不是数据本身（见 `documentation/core/dependency.md §3.4`）。
- 外部调用经事件订阅者/应用服务触发，不进本聚合 DB 事务。
- 写外部幂等：适配器继承 `AbstractIdempotentWriteGateway`，以 `bizId` 先查后写。

## 4. 落地骨架

```java
// 包：io.pragmatic.ddd.dependency.{ExternalDependency, IDependency, DependencyType}
@ExternalDependency(targetName = "User", type = DependencyType.AGGREGATE,
        description = "用户聚合：提供等级/资格判定")
public interface IUserDependency extends IDependency {
    int getUserLevel(String userId);
    boolean isUserQualified(String userId);
}

@Component
public class UserGatewayAdapter implements IUserDependency {
    @Override public int getUserLevel(String userId) { return 0; }   // 打桩
}

// 写外部幂等
@ExternalDependency(targetName = "PointsService", type = DependencyType.EXTERNAL_SYSTEM, ...)
public interface IUserPointsDependency extends IDependency {
    void increasePoints(IncreasePointsCommand command);   // command 携带 bizId
}
@Component
public class UserPointsDependencyAdapter
        extends AbstractIdempotentWriteGateway<IncreasePointsCommand, Void, PointsRequest, PointsResponse, String>
        implements IUserPointsDependency {
    @Override public void increasePoints(IncreasePointsCommand command) { write(command); }
    @Override protected String uniqueKey(IncreasePointsCommand param) { return param.bizId(); }
}

// 外部返回值转领域值后可入聚合：单值标识符用 String，直接作为聚合字段落库
@ExternalDependency(targetName = "inventory", type = DependencyType.EXTERNAL_SYSTEM,
        description = "库存系统：预占库存并返回预占单号")
public interface IInventoryDependency extends IDependency {
    String reserve(String sku, int qty);     // 返回预占单号（领域值）
    void release(String reserveNo);          // 逆向操作，须幂等
}
public class Order extends AggregateRoot<Long> {
    private String inventoryReservationNo;   // 后续核销/释放/对账的依据
}
```

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 领域层直接写 HTTP/RPC | 定义 `I{目标}Dependency` + 基础设施 ACL |
| `targetName` 与适配器不一致 | 声明与实现用同一全局唯一 `targetName` |
| 把 `@ExternalDependency` 当运行期可用性保证 | 声明只管血缘，注入缺失由容器负责 |
| 把外部 Response/DTO 直接作聚合字段 | ACL `toDomainResult` 转为领域值后再入聚合 |
| 以为「不持久化外部返回值」而不能存外部标识符 | 禁的是外部契约对象；转换后的领域值（如 `String` 预占单号）必须落库 |
| 调用行为/异常分类写进依赖接口 | 接口只定义签名，实现侧封装转换 |
| 只写 ACL 不声明 | 先定义端口再实现适配器 |
| 一个接口塞多目标 | 一目标一端口 |
| 写外部不幂等 | 继承 `AbstractIdempotentWriteGateway`，`bizId` 先查后写 |

## 6. 被读于

S4 `ddd-aggregate-create`（端口装配）、S7 `ddd-code-review`。
