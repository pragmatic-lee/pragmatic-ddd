# BP 卡：外部依赖声明落地模式（dependency-declaration）

> 源：`documentation/best-practices/dependency-declaration.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

外部依赖声明（`io.pragmatic.ddd.dependency`）是**领域层元数据声明**：描述「本聚合依赖了哪个外部目标」，不负责远程调用。与 `@DomainService` 对称——一个声明「需要什么能力」，一个声明「依赖什么外部聚合/系统」。

- 真正调用/协议转换/异常分类由**防腐适配器（ACL）**在基础设施层实现。
- 声明即血缘：即便运行期不强制校验，声明仍是架构可视化/依赖分析唯一来源（先定义后实现，目标未就绪先声明端口+打桩）。

| 维度 | 外部依赖声明（领域层） | 防腐适配器（基础设施层） |
| --- | --- | --- |
| 形态 | `@ExternalDependency` + `IDependency` 接口 | `@Component` 实现领域端口 |
| 职责 | 声明「依赖了什么」 | 封装「怎么调用外部」 |
| 远程调用 | 否 | 是 |

## 2. 命名与包结构

```text
domain/{agg}/dependency/    I{目标}Dependency（继承 IDependency，标 @ExternalDependency）
infrastructure/{agg}/dependency/  {目标}GatewayAdapter（@Component 实现领域端口）
```

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 依赖声明接口 | `I{目标}Dependency`（继承 `IDependency`，标 `@ExternalDependency`） | `IUserDependency` |
| 外部系统接口 | `I{目标系统}ClientDependency`（`type=EXTERNAL_SYSTEM`） | `IPaymentGatewayDependency` |
| `targetName` | 小写下划线/连字符，全局唯一，与适配器一致 | `user` / `payment-gateway` |
| 适配器 | `{目标}GatewayAdapter`/`{目标}ClientAdapter`（`@Component`） | `UserGatewayAdapter` |

## 3. 落地方式（核心）

1. **领域层声明**：`domain/{agg}/dependency/` 下接口 `extends IDependency` + `@ExternalDependency(targetName="User", type=DependencyType.AGGREGATE, description="...")`；`targetName` 必填全局唯一，`type` 默认 `AGGREGATE`，跨系统显式 `EXTERNAL_SYSTEM`。
2. **ACL 实现**：`infrastructure/` 下 `@Component implements I{目标}Dependency`，封装调用；目标未就绪先打桩。
3. **应用层消费**：服务/事件订阅者构造注入端口消费；**领域模型不感知远程调用**。
4. **写外部+幂等**：声明端口后适配器继承 `AbstractIdempotentWriteGateway<P,R,ExtReq,ExtResp,K>`，以 `bizId`（业务幂等键）先查后写：
   ```java
   @Component
   public class UserPointsDependencyAdapter
       extends AbstractIdempotentWriteGateway<IncreasePointsCommand, Void, PointsRequest, PointsResponse, String>
       implements IUserPointsDependency {
       @Override public void increasePoints(IncreasePointsCommand command) { write(command); }
       @Override protected String uniqueKey(IncreasePointsCommand p) { return p.bizId(); }
       // toExternalRequest / doWrite / queryByKey 等钩子封装真实调用
   }
   ```
5. **外部返回值转换后可入领域**：ACL `toDomainResult` 转为领域值（基本类型/String/值对象/枚举）后，可作为聚合字段随本聚合事务落库；单值标识符用 `String`/基本类型，复合结构才用值对象。

## 4. 关键机制与避坑

- `targetName` 必填、全局唯一，须与适配器指向的外部目标一致（血缘可视化关联）。
- 运行期不强制校验：声明未实现/不可用不报错；端口注入缺失由容器负责。
- 仅标记「依赖了什么」：远程调用/协议转换/异常分类归 ACL，禁塞进依赖接口。
- 接口以 `I` 开头、继承 `IDependency`，供架构扫描识别。
- 消费在应用层：外部调用经订阅者/应用服务触发，**不进本聚合 DB 事务**，避免外部不可用致本地事务回滚风暴。
- 被禁入领域的是**外部契约对象**（对方 Response/DTO），不是数据本身；转换后领域值可入聚合。

> ⚠️ `targetName` 不一致是高频坑：声明与实现用同一全局唯一 `targetName`。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 领域层直接写 HTTP/RPC | 定义 `I{目标}Dependency` + 基础设施 ACL |
| 外部 Response/DTO 直接作聚合字段 | ACL `toDomainResult` 转领域值后再入聚合 |
| `targetName` 与适配器目标不一致 | 声明与实现同一全局唯一 `targetName` |
| 把 `@ExternalDependency` 当运行时可用性保证 | 端口注入缺失由容器负责，声明只管血缘 |
| 调用/异常分类写进依赖接口 | 接口只定义签名，实现侧封装转换 |
| 只写 ACL 不声明 | 先定义端口再实现适配器 |
| 一个接口塞多个目标 | 一目标一端口（一接口一职责） |
| 写外部不幂等 | 继承 `AbstractIdempotentWriteGateway`，以 `bizId` 先查后写 |

## 6. 被读于

S5 `ddd-aggregate-extend`、S7 `ddd-code-review`、咨询。
