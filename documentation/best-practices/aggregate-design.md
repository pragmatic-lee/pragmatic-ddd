# 聚合设计原则

> 本文档介绍使用 Pragmatic DDD 进行聚合设计的最佳实践与常见反模式。

## 1. 聚合设计原则

### 1.1 小聚合原则

聚合根应尽量小，只包含**必须保证一致性**的子实体。一个聚合根包含的字段和子实体越多，锁竞争越激烈，并发性能越差。

```java
// ✅ 推荐：Order 聚合只持有 OrderItem 的引用
public class Order extends AggregateRoot<Long> {
    private TrackedList<OrderItem, Long> items;
    private String status;
    private long amount;
}

// ❌ 反模式：Order 聚合包含 Customer 和 Product 的完整数据
public class Order extends AggregateRoot<Long> {
    private Customer customer;    // 应改为 customerId
    private List<Product> products; // 应改为 productIds
}
```

### 1.2 ID 引用而非对象引用

跨聚合引用时，只持有对方的 ID，不持有对象引用：

```java
// ✅ 推荐
public class Order extends AggregateRoot<Long> {
    private Long customerId;  // 只持有 ID
    private TrackedList<OrderItem, Long> items;
}

// ❌ 反模式
public class Order extends AggregateRoot<Long> {
    private Customer customer;  // 持有完整对象引用
}
```

### 1.3 事务边界 = 单聚合根

一次事务只修改一个聚合根。需要跨聚合根操作时：

- 单聚合根命令：`CommandExecutor`
- 跨聚合根事务：`UnitOfWork`（谨慎使用，影响并发）
- 更推荐：通过领域事件解耦，最终一致

```java
// ✅ 推荐：通过事件解耦
order.cancel();  // 只改 Order 聚合
// OrderCancelledEvent → 触发库存释放（异步）

// ⚠️ 谨慎：跨聚合根事务
unitOfWork.register(order, orderRule, orderRepo, Order::cancel)
          .register(inventory, inventoryRule, inventoryRepo, Inventory::release)
          .commit();
```

## 2. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 大聚合根 | 锁竞争、性能差 | 拆分小聚合，ID 引用 |
| 跨聚合引用对象 | 加载整个对象图 | 只持有 ID |
| 聚合根之间直接调用 | 耦合、事务边界模糊 | 通过领域事件解耦 |
| 领域逻辑泄漏到应用层 | 贫血模型 | 领域逻辑内聚到聚合根 |
| 仓储返回 DTO | 混淆读写模型 | 写走 `IRepository`，读走 `IAggregateProjection` |

## 3. 异常处理策略

```
PragmaticException             框架所有业务异常的抽象基类
 └── RuleException             业务规则校验异常基类
      └── BrokenRuleException          单条规则违反（code + message + source）
      └── BrokenRuleAggregateException 聚合规则违反（含全部违反）
```

推荐处理方式：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BrokenRuleException.class)
    public ResponseEntity<ErrorResponse> handleBrokenRule(BrokenRuleException e) {
        // 把 code 映射为前端友好的错误码
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BrokenRuleAggregateException.class)
    public ResponseEntity<ErrorResponse> handleAggregate(BrokenRuleAggregateException e) {
        // 返回全部违反信息
        List<ErrorResponse.FieldError> errors = e.getBrokenRules().stream()
                .map(r -> new ErrorResponse.FieldError(r.getName(), r.getDescription()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("AGGREGATE_VIOLATION", "校验失败", errors));
    }

    @ExceptionHandler(PragmaticException.class)
    public ResponseEntity<ErrorResponse> handlePragmatic(PragmaticException e) {
        // 兜底捕获
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL", e.getMessage()));
    }
}
```

## 4. 项目分包建议

```
com.example.order/
├── domain/                 # 领域层
│   ├── model/              # 聚合根、实体、值对象
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── Address.java
│   ├── rule/              # 规则
│   │   ├── OrderRule.java
│   │   └── OrderRuleRegistry.java
│   ├── event/             # 领域事件
│   │   └── OrderCancelledEvent.java
│   ├── operation/         # 操作注册表
│   │   └── OrderOperationRegistry.java
│   └── service/           # 领域服务
│       └── TransferService.java
├── application/           # 应用层
│   ├── command/           # 命令服务
│   │   └── OrderCommandService.java
│   ├── query/             # 查询服务
│   │   └── OrderQueryService.java
│   ├── factory/           # 实体工厂
│   └── updater/           # 实体更新器
├── infrastructure/        # 基础设施层
│   ├── persistence/       # 仓储实现
│   │   ├── OrderRepositoryImpl.java
│   │   └── OrderMapper.java
│   ├── event/             # 事件管理器
│   │   └── EventManagerConfig.java
│   └── outbox/            # Outbox 实现
│       └── OutboxConfig.java
└── interfaces/            # 接口层
    ├── rest/              # REST Controller
    │   └── OrderController.java
    └── rpc/               # RPC 入口
```

---

下一步：

- [事件建模指南](./event-modeling.md)
- [事务性发件箱](./transactional-outbox.md)
