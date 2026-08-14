# 防腐层（ACL）

> 本文档说明 `io.pragmatic.ddd.acl` 包提供的防腐层（Anti-Corruption Layer）调用套路能力：统一收敛"领域对象 ↔ 外部系统"之间的请求/响应转换、通信与异常分类。相关文档：[领域事件](./domain-event.md) · [领域服务](./domain-service.md) · [仓储](./repository.md)。

## 1. 概述

### 1.1 核心定位

防腐层隔离外部系统的模型与内部领域模型，避免外部契约污染领域层。本包将"领域入参 → 请求转换 → 调用对方 → 响应转换 → 领域返回值"这一固定套路收敛为可复用的模板，并提供**两种使用形态**：

| 形态 | 入口 | 风格 | 适用 |
| --- | --- | --- | --- |
| 组合式 | `ExternalCall` 静态方法 | 传入 `Function` 参数，不要求继承 | 适配器体量小、需灵活组合 |
| 继承式 | `AbstractQueryGateway` / `AbstractWriteGateway` / `AbstractIdempotentWriteGateway` 抽象类 | 重写抽象方法 | 套路固定、需复用父类状态 |

两种形态共享同一套异常分类（`AclConversionException` / `AclCommunicationException`）与日志钩子（`ExternalCallLogger`）。

### 1.2 设计目标

- **统一套路**：四类调用（查询、写入、先查后写）的节点顺序固定，降低样板代码。
- **异常二分**：本地转换失败（不可重试）与外部通信失败（通常可重试）明确区分，避免重试策略误判。
- **零日志耦合**：通过 `ExternalCallLogger` 钩子抽象埋点，不绑定具体日志框架。

### 1.3 概念层级

```text
ExternalCall（组合式静态入口）
  ├─ query           查询
  ├─ write           写入
  └─ writeIdempotent 先查后写

AbstractQueryGateway       查询（继承式）
AbstractWriteGateway       写入（继承式）
AbstractIdempotentWriteGateway  先查后写（继承式，幂等保护）

AclExceptions         异常包装辅助（convert / communicate）
ExternalCallLogger    日志钩子（onRequest / onResponse / onError）
AclException          ACL 异常基类（继承 PragmaticException）
  ├─ AclConversionException   转换失败（不可重试）
  └─ AclCommunicationException 通信失败（通常可重试）
```

## 2. 核心概念详解

### 2.1 通用套路节点

所有套路均按以下顺序执行，并在每个节点接入日志钩子：

```text
[toExternalRequest 请求转换] → logger.onRequest
                              → [doQuery/doWrite 调用对方] → logger.onResponse
                                                          → [toDomainResult 响应转换]
```

先查后写（`writeIdempotent`）额外在开头插入"查重"节点：

```text
[uniqueKey 查重键提取] → [queryByKey 查重查询] ──存在──► [toDomainResultFromExisting] 短路返回
                                                  └─不存在─► 走上述写入套路
```

### 2.2 `ExternalCall`（组合式）

`ExternalCall` 提供三个静态方法，直接以 `Function` 参数描述各节点，无需继承。

#### 2.2.1 查询

```java
ExternalCall.query(
        orderId,
        this::toRequest,                 // 领域入参 → 对方入参
        this::doQuery,                   // 调用对方查询接口
        this::toResult                   // 对方返回值 → 领域返回值
);
```

带日志钩子：

```java
ExternalCall.query(orderId, this::toRequest, this::doQuery, this::toResult, logger);
```

#### 2.2.2 写入

```java
ExternalCall.write(
        command,
        this::toRequest,
        this::doWrite,
        this::toResult
);
```

#### 2.2.3 先查后写（幂等保护）

```java
ExternalCall.writeIdempotent(
        command,
        this::uniqueKey,          // 提取查重唯一键
        this::queryByKey,         // 按唯一键查询，返回 Optional
        this::toExistingResult,   // 已存在 → 领域返回值
        this::toRequest,          // 不存在 → 请求转换
        this::doWrite,            // 调用对方写入
        this::toResult            // 响应转换
);
```

:::: warning 幂等是概率性保障
`writeIdempotent` 仅"降低重复概率"。并发场景下两个请求可能同时通过查重，真正幂等仍需**对方写入接口按唯一键去重**。本包不提供分布式锁。
::::

### 2.3 继承式抽象类

当套路固定、需在多个适配器中复用时，继承对应抽象类并重写抽象方法。

#### 2.3.1 查询网关 `AbstractQueryGateway<P, R, Q, S>`

| 抽象方法 | 职责 |
| --- | --- |
| `Q toExternalRequest(P param)` | 领域入参 → 对方入参 |
| `S doQuery(Q request)` | 调用对方查询接口 |
| `R toDomainResult(S response)` | 对方返回值 → 领域返回值 |

```java
public class OrderQueryGateway extends AbstractQueryGateway<OrderId, OrderView, ExternalQuery, ExternalResp> {

    @Override
    protected ExternalQuery toExternalRequest(OrderId param) {
        return new ExternalQuery(param.value());
    }

    @Override
    protected ExternalResp doQuery(ExternalQuery request) {
        return externalClient.query(request);
    }

    @Override
    protected OrderView toDomainResult(ExternalResp response) {
        return OrderView.from(response);
    }
}

// 调用
OrderView view = new OrderQueryGateway().query(orderId);
```

#### 2.3.2 写入网关 `AbstractWriteGateway<P, R, Q, S>`

方法与查询网关对称，仅 `doWrite(Q request)` 改为调用对方写入接口：

```java
public class PaymentWriteGateway extends AbstractWriteGateway<PayCommand, PayResult, PayReq, PayResp> {

    @Override
    protected PayReq toExternalRequest(PayCommand param) { /* ... */ }

    @Override
    protected PayResp doWrite(PayReq request) {
        return externalClient.write(request);
    }

    @Override
    protected PayResult toDomainResult(PayResp response) { /* ... */ }
}
```

#### 2.3.3 先查后写网关 `AbstractIdempotentWriteGateway<P, R, Q, S, K>`

比写入网关多三个抽象方法，用于"查重 → 短路"：

| 抽象方法 | 职责 |
| --- | --- |
| `K uniqueKey(P param)` | 提取查重唯一键 |
| `Optional<S> queryByKey(K key)` | 按唯一键查询，空表示未处理过 |
| `R toDomainResultFromExisting(S existing)` | 已存在记录 → 领域返回值（需结合状态判断终态） |

```java
public class IdempotentWriteGateway
        extends AbstractIdempotentWriteGateway<Cmd, Result, Req, Resp, String> {

    @Override
    protected String uniqueKey(Cmd param) {
        return param.bizKey();
    }

    @Override
    protected Optional<Resp> queryByKey(String key) {
        return externalClient.findByKey(key);
    }

    @Override
    protected Result toDomainResultFromExisting(Resp existing) {
        // 结合状态判断是否为终态成功，再映射为领域返回值
        return Result.fromExisting(existing);
    }

    @Override
    protected Req toExternalRequest(Cmd param) { /* ... */ }

    @Override
    protected Resp doWrite(Req request) {
        return externalClient.write(request);
    }

    @Override
    protected Result toDomainResult(Resp response) { /* ... */ }
}
```

### 2.4 `ExternalCallLogger` 日志钩子

日志钩子抽象了三个关键节点，默认空实现，业务侧按需覆盖，不绑定任何日志框架：

| 钩子方法 | 触发时机 |
| --- | --- |
| `onRequest(Q request)` | 请求转换完成后、调用对方前 |
| `onResponse(S response)` | 收到对方响应、响应转换前 |
| `onError(Throwable ex)` | 转换或通信发生异常时 |

类型安全空实现工厂：`ExternalCallLogger.noop()`。继承式网关可通过 `setLogger(...)` 注入：

```java
ExternalCallLogger<ExternalQuery, ExternalResp> logger = new ExternalCallLogger<>() {
    @Override
    public void onRequest(ExternalQuery request) {
        log.info("ACL request: {}", request);
    }

    @Override
    public void onResponse(ExternalResp response) {
        log.info("ACL response: {}", response);
    }

    @Override
    public void onError(Throwable ex) {
        log.error("ACL error", ex);
    }
};

gateway.setLogger(logger);            // 继承式
ExternalCall.query(p, r, c, s, logger);  // 组合式
```

## 3. 关键机制与避坑指南

### 3.1 异常二分机制

`AclExceptions` 统一包装两类失败，节点语义固定：

| 包装方法 | 节点 | 失败归类 | 可重试性 |
| --- | --- | --- | --- |
| `AclExceptions.convert(...)` | 请求转换 / 响应转换 / 查重键提取 / 已存在记录转换 | `AclConversionException` | 否（本地问题） |
| `AclExceptions.communicate(...)` | 查询调用 / 写入调用 / 查重查询 | `AclCommunicationException` | 是（通常） |

若节点已主动抛出 `AclException`（含其子类），`AclExceptions` 会**原样传递**，不重复包装，避免因果嵌套。

```text
转换步骤抛 RuntimeException → wrap 为 AclConversionException（保留 cause）
通信步骤抛 RuntimeException → wrap 为 AclCommunicationException（保留 cause）
步骤抛 AclException          → 原样抛出（不嵌套）
每个异常路径均触发 logger.onError(原始异常)
```

:::: warning 不要混用异常
本地转换失败被归为 `AclConversionException`（不可重试）。若把"网络超时"在转换阶段抛出，会被误判为不可重试，导致上层重试策略失效。通信失败应在 `doQuery` / `doWrite` / `queryByKey` 阶段抛出，由 `communicate` 统一归为 `AclCommunicationException`。
::::

### 3.2 组合式 vs 继承式选型

| 维度 | 组合式（`ExternalCall`） | 继承式（抽象类） |
| --- | --- | --- |
| 扩展方式 | 传入 `Function` | 继承重写抽象方法 |
| 复用父类状态 | 否 | 是（如共享 `client` / `logger`） |
| 多套路混合 | 易组合 | 受单继承限制 |
| 样板代码 | 调用处集中 | 类级分散 |

规则：适配器仅调用单个套路且体量小 → 组合式；多个适配器共享同一套节点实现或需持有状态 → 继承式。

### 3.3 先查后写的短路语义

`writeIdempotent` 在查重命中时**短路返回** `toDomainResultFromExisting(existing.get())`，不再发起写入。短路路径同样触发 `logger.onResponse(existing.get())`，但不触发 `onRequest`（因为未构造写入请求）。业务侧在 `toDomainResultFromExisting` 内部应结合记录状态判断是否为终态成功（例如已成功的支付不应重复通知）。

### 3.4 查询套路的无副作用假设

`ExternalCall.query` / `AbstractQueryGateway.query` 假定查询无副作用，调用失败可由上层直接重试。若查询接口实际产生副作用（如计数、发券），不应使用查询套路，应改用写入套路并在上层明确重试边界。

## 4. 异常与错误处理体系

### 4.1 异常类层次

```text
PragmaticException（框架统一异常基类，可 catch 兜底）
  └─ AclException（ACL 异常基类）
       ├─ AclConversionException   转换失败，不可重试
       └─ AclCommunicationException 通信失败，通常可重试
```

所有异常保留原始 `cause`，便于定位根因。

### 4.2 异常触发与处理建议

| 异常 | 触发节点 | 原始 cause 示例 | 处理建议 |
| --- | --- | --- | --- |
| `AclConversionException` | 请求/响应转换、查重键提取、已存在记录转换 | 字段缺失、类型不兼容、空指针 | 修复入参或映射，不重试；必要时告警 |
| `AclCommunicationException` | 查询/写入调用、查重查询 | 超时、连接拒绝、远程业务错误 | 上层决策重试/降级/熔断 |

### 4.3 错误用法汇总

| 错误用法 | 后果 | 正确做法 |
| --- | --- | --- |
| 在转换阶段抛出网络超时 | 归为 `AclConversionException`，重写试策略失效 | 超时应在 `doQuery`/`doWrite` 抛出 |
| 把有副作用的查询放进 query 套路 | 重试导致重复副作用 | 改用 write 套路 |
| 把 `writeIdempotent` 当强幂等 | 并发重复写入 | 依赖对方唯一键去重 + 上层幂等控制 |
| 在 `toDomainResultFromExisting` 忽略状态 | 重复终态被当新成功 | 结合状态判断终态 |

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| `ExternalCall` | `query` / `write` / `writeIdempotent` 静态方法 | 组合式；可传 `ExternalCallLogger` |
| `AbstractQueryGateway` | 继承 + 重写 3 个方法 | 查询无副作用，可重试 |
| `AbstractWriteGateway` | 继承 + 重写 3 个方法 | 写入有副作用，通信异常上抛 |
| `AbstractIdempotentWriteGateway` | 继承 + 重写 6 个方法 | 先查后写仅概率幂等，靠对方去重 |
| `ExternalCallLogger` | `setLogger` 或末参传入 | 不绑定日志框架；`noop()` 默认空 |
| `AclConversionException` | 转换失败 | 不可重试 |
| `AclCommunicationException` | 通信失败 | 通常可重试 |

**下一步阅读**

- [领域服务](./domain-service.md)：编排跨聚合的领域逻辑
- [仓储](./repository.md)：聚合持久化
- [领域事件](./domain-event.md)：跨上下文最终一致
