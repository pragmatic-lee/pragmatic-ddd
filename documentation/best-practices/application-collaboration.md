# 应用服务层协作

> 本文档介绍应用服务层如何与聚合根协作：业务方法内「先记录操作、后收集事件」的顺序、ID 后生成场景的延迟事件、事件发布后的工作单元清理，以及规则校验异常的响应映射。前置阅读：[聚合设计原则](./aggregate-design.md)。

## 1. 操作与事件的顺序

每次业务行为必须**先 `recordOperation`，后 `collectEvent`**。框架的事件会自动回填 `operationCode`（取最近一次操作）与 `version`。如果先收集事件再记录操作，事件因缺少成因而抛 `OperationException`。

```java
this.recordOperation(PersonOperationRegistry.UPDATE); // 先
this.collectEvent(PersonUpdatedEvent.buildEvent(this)); // 后
```

若希望事件成因与「最近操作」解耦，使用 `collectEvent(event, triggerOperation)` 显式指定。

## 2. 延迟事件：ID 后生成必用

当聚合根 ID 由持久化后生成（自增主键、仓储回填雪花 ID），**构造期 `getEntityId()` 还是 `null`**。若用立即事件，事件会定格错误的 `entityId`，且无法补救。

框架提供延迟事件重载 `collectEvent(Supplier<IDomainEvent>)`：`Supplier` 在事件真正发布时才执行，届时读到真实 ID。

```java
// ID 构造期未知 → 强制延迟事件
this.collectEvent(() -> PersonCreatedEvent.buildEvent(this));
```

判定原则：**构造期拿不到确定 ID，一律用延迟事件**；ID 由业务传入（UUID / 雪花 ID）时，可用立即事件 `collectEvent(PersonCreatedEvent.buildEvent(this))`。

## 3. 事件发布后的清理

应用层在事件分发完成后，必须调用 `clearWorkUnitState()` 清空已收集的事件、操作与因果指针，避免同一工作单元被重复处理或跨请求串味。

```java
order.getDomainEvents().forEach(eventManager::publish);  // 分发
order.clearWorkUnitState();                              // 清理
```

> `CommandExecutor` / `UnitOfWork` 的固定模板已内置「发布事件 → 清理状态」两步（见 [核心：应用服务](../core/application-service.md)），继承 `AbstractApplicationService` 或使用执行器时无需手动清理；仅在自研编排时需要注意。

## 4. 规则校验异常的响应映射

### 4.1 异常继承体系

```
PragmaticException             框架所有业务异常的抽象基类
 └── RuleException             业务规则校验异常基类
      └── BrokenRuleException          单条规则违反（code + message + source）
      └── BrokenRuleAggregateException 聚合规则违反（含全部违反）
```

### 4.2 统一响应示例

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

异常体系的完整字段与捕获规范见 [核心：领域建模](../core/domain-modeling.md) 的「异常与错误处理体系」章节。

---

## 下一步

- [聚合设计原则](./aggregate-design.md)
- [校验规则领域服务](./rule-validation.md)：校验的编排与触发
- [事件建模指南](./event-modeling.md)
- [核心：应用服务](../core/application-service.md)：`CommandExecutor` / `UnitOfWork` / Outbox
