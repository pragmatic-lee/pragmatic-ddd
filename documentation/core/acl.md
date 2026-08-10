# 防腐层（ACL）

> 本文档介绍防腐层（`io.pragmatic.ddd.acl`）的使用，包括与外部系统交互的固定套路、异常分类与日志钩子。
> 前置阅读：[领域建模](../core/domain-modeling.md)。

## 1. 概述

防腐层（Anti-Corruption Layer，ACL）用于隔离领域模型与外部系统（其他聚合、第三方 API、遗留服务），保证领域层不被外部模型"污染"。

框架提供两种使用方式：

| 方式 | 特点 | 适用场景 |
| --- | --- | --- |
| **继承式**（`AbstractQueryGateway` / `AbstractWriteGateway`） | 继承抽象类，实现抽象方法 | 适配器有明确类继承关系 |
| **组合式**（`ExternalCall` 静态方法） | 以函数参数提供，不要求继承 | 适配器已继承其他类，或偏好组合 |

两者内部逻辑完全一致，均按固定套路执行并自动分类异常。

### 固定套路

```
领域入参 → 请求转换 → 调用对方 → 响应转换 → 领域返回值
              ↓           ↓           ↓
         AclConversion  AclCommunication  AclConversion
         Exception      Exception          Exception
         (不可重试)      (可重试)           (不可重试)
```

## 2. 异常体系

```
PragmaticException
 └── AclException                    ACL 异常抽象基类
      ├── AclConversionException     本地转换异常（不可重试）
      └── AclCommunicationException  外部通信异常（可重试）
```

| 异常 | 语义 | 典型场景 | 重试策略 |
| --- | --- | --- | --- |
| `AclConversionException` | 本地数据转换失败 | 请求转换、响应转换、查重键提取 | **不可重试**，需修复入参或映射 |
| `AclCommunicationException` | 外部通信失败 | 网络超时、远程业务错误、非预期状态码 | **可重试**，由上层决策重试/降级/熔断 |

```java
try {
    return externalService.query(param);
} catch (AclConversionException e) {
    // 不可重试：记录日志并返回错误
    log.error("转换失败", e);
    throw e;
} catch (AclCommunicationException e) {
    // 可重试：重试或降级
    return retryOrFallback(param, e);
}
```

## 3. 日志钩子 `ExternalCallLogger`

`ExternalCallLogger<Q, S>` 在调用关键节点触发，便于输出日志、埋点、链路追踪。不绑定任何日志框架：

```java
public interface ExternalCallLogger<Q, S> {

    default void onRequest(Q request) { }    // 请求转换完成后、调用对方前

    default void onResponse(S response) { }  // 收到对方响应、响应转换前

    default void onError(Throwable ex) { }   // 调用或转换发生异常时
}
```

使用示例：

```java
ExternalCallLogger<ExternalOrderReq, ExternalOrderResp> logger = new ExternalCallLogger<>() {
    @Override
    public void onRequest(ExternalOrderReq request) {
        log.info("调用外部订单接口, req={}", request);
    }

    @Override
    public void onResponse(ExternalOrderResp response) {
        log.info("外部订单接口返回, resp={}", response);
    }

    @Override
    public void onError(Throwable ex) {
        log.error("外部调用异常", ex);
    }
};
```

默认 `NOOP` 空实现，不输出任何内容。

## 4. 继承式：查询网关 `AbstractQueryGateway`

```java
public class ExternalOrderQueryGateway
        extends AbstractQueryGateway<OrderQuery, OrderDTO, ExternalOrderReq, ExternalOrderResp> {

    private final ExternalOrderClient client;

    public ExternalOrderQueryGateway(ExternalOrderClient client) {
        this.client = client;
    }

    @Override
    protected ExternalOrderReq toExternalRequest(OrderQuery param) {
        // 领域入参 → 对方接口入参
        return new ExternalOrderReq(param.getOrderId());
    }

    @Override
    protected ExternalOrderResp doQuery(ExternalOrderReq request) {
        // 调用对方查询接口
        return client.queryOrder(request);
    }

    @Override
    protected OrderDTO toDomainResult(ExternalOrderResp response) {
        // 对方返回值 → 领域返回值
        return new OrderDTO(response.getId(), response.getStatus());
    }
}
```

使用：

```java
ExternalOrderQueryGateway gateway = new ExternalOrderQueryGateway(client);
gateway.setLogger(logger);  // 可选：设置日志钩子
OrderDTO result = gateway.query(new OrderQuery("ORD-001"));
```

模板内部流程：

```
query(param)
  ├─ AclExceptions.convert(() -> toExternalRequest(param))   ← 转换失败 → AclConversionException
  ├─ logger.onRequest(request)
  ├─ AclExceptions.communicate(() -> doQuery(request))       ← 通信失败 → AclCommunicationException
  ├─ logger.onResponse(response)
  └─ AclExceptions.convert(() -> toDomainResult(response))   ← 转换失败 → AclConversionException
```

## 5. 继承式：写入网关 `AbstractWriteGateway`

```java
public class ExternalOrderWriteGateway
        extends AbstractWriteGateway<CreateOrderCmd, String, ExternalCreateReq, ExternalCreateResp> {

    private final ExternalOrderClient client;

    @Override
    protected ExternalCreateReq toExternalRequest(CreateOrderCmd param) {
        return new ExternalCreateReq(param.getOrderId(), param.getAmount());
    }

    @Override
    protected ExternalCreateResp doWrite(ExternalCreateReq request) {
        return client.createOrder(request);
    }

    @Override
    protected String toDomainResult(ExternalCreateResp response) {
        return response.getExternalId();
    }
}
```

使用：

```java
String externalId = gateway.write(new CreateOrderCmd("ORD-001", 100));
```

::: tip 通信异常 vs 转换异常
写入场景中，`AclCommunicationException` 应向上抛出由上层重试；`AclConversionException` 不应被误判为"可重试的写超时"，它表示本地数据有问题，重试也不会成功。
:::

## 6. 继承式：幂等写入网关 `AbstractIdempotentWriteGateway`

"先查后写"套路，用唯一键查询对方，已存在则短路返回：

```java
public class IdempotentOrderCreateGateway
        extends AbstractIdempotentWriteGateway<CreateOrderCmd, String, ExternalCreateReq, ExternalCreateResp, String> {

    @Override
    protected String uniqueKey(CreateOrderCmd param) {
        return param.getOrderId();  // 查重唯一键
    }

    @Override
    protected Optional<ExternalCreateResp> queryByKey(String key) {
        return client.findByOrderId(key);  // 查对方是否已处理
    }

    @Override
    protected String toDomainResultFromExisting(ExternalCreateResp existing) {
        return existing.getExternalId();  // 已存在则短路返回
    }

    @Override
    protected ExternalCreateReq toExternalRequest(CreateOrderCmd param) { ... }

    @Override
    protected ExternalCreateResp doWrite(ExternalCreateReq request) { ... }

    @Override
    protected String toDomainResult(ExternalCreateResp response) { ... }
}
```

模板流程：

```
write(param)
  ├─ 提取查重唯一键
  ├─ 查重查询 → 已存在？→ 短路返回（转换已存在记录）
  └─ 不存在 → 请求转换 → 写入调用 → 响应转换
```

::: warning 幂等保证
本套路仅**降低重复概率**，真正幂等仍需对方写入接口按唯一键去重。
:::

## 7. 组合式：`ExternalCall` 静态方法

不要求继承，以函数参数提供，适合适配器已继承其他类的场景：

```java
// 查询
OrderDTO result = ExternalCall.query(
        new OrderQuery("ORD-001"),
        OrderQuery::toExternalRequest,      // 领域入参 → 对方入参
        client::queryOrder,                 // 调用对方
        ExternalOrderResp::toDomainResult,  // 对方返回 → 领域返回
        logger);                            // 可选日志钩子

// 写入
String externalId = ExternalCall.write(
        new CreateOrderCmd("ORD-001", 100),
        CreateOrderCmd::toExternalRequest,
        client::createOrder,
        ExternalCreateResp::getExternalId);

// 幂等写入
String result = ExternalCall.writeIdempotent(
        new CreateOrderCmd("ORD-001", 100),
        CreateOrderCmd::getOrderId,         // 查重键提取
        client::findByOrderId,              // 查重查询
        ExternalCreateResp::getExternalId,  // 已存在记录转换
        CreateOrderCmd::toExternalRequest,  // 请求转换
        client::createOrder,                // 写入调用
        ExternalCreateResp::getExternalId); // 响应转换
```

`ExternalCall` 提供三个静态方法：

| 方法 | 说明 |
| --- | --- |
| `query(param, toRequest, doCall, toResult)` | 查询套路（无副作用） |
| `write(param, toRequest, doCall, toResult)` | 写入套路（有副作用） |
| `writeIdempotent(param, toKey, queryByKey, toResultExisting, toRequest, doCall, toResult)` | 幂等写入（先查后写） |

每个方法均有带 `ExternalCallLogger` 的重载版本。

## 8. 外部依赖声明

> 聚合的**外部依赖声明**（标注本聚合依赖了哪些外部聚合 / 系统）已从本包移出，独立为 `io.pragmatic.ddd.dependency` 包，详见 [外部依赖声明](./dependency.md)。
>
> 防腐层（ACL）只负责**外部调用的封装机制**（转换、通信、异常分类、日志）；而"依赖了什么"是领域层对外部契约的声明，二者正交
<arg_key:6124c78e>explanation</arg_key:6124c78e>
<arg_value:6124c78e>将 acl.md 第8节改为指向 dependency 文档的跳转，并在概述中强调 ACL 与依赖声明正交

## 9. 继承式 vs 组合式选型

| 维度 | 继承式 | 组合式 |
| --- | --- | --- |
| 代码组织 | 适配器有明确类层级 | 适配器可继承其他类 |
 | 可读性 | 方法名语义清晰 | 函数参数链式 |
 | 复用性 | 单类内聚全部转换逻辑 | 可拆散到多个函数 |
 | 灵活性 | 受限于继承 | 自由组合 |

选择建议：

- 适配器是独立类，只做外部调用 → **继承式**
- 适配器已继承其他类，或需要灵活组合 → **组合式**

---

下一步：

- [配置体系](./configuration.md)
- [对外广播](./broadcast.md)
- [异常处理策略](../best-practices/aggregate-design.md)
