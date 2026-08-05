/**
 * Pragmatic DDD —— 务实、轻量的领域驱动设计框架。
 *
 * <p>聚焦 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、业务规则与领域事件，
 * 并配套应用层（工作单元 / 命令执行 / 事务性 Outbox）与仓储基础设施
 * （写模型持久化 / 读模型查询与投影 / 读模型对账）。</p>
 *
 * <p>核心包一览：</p>
 * <ul>
 *   <li>{@code base} —— 实体与聚合根基类、值对象、规则违反收集、统一异常及号段 ID 生成（{@code base.id}）</li>
 *   <li>{@code rules} —— 无状态业务规则容器（EntityRule）与两级激活条件</li>
 *   <li>{@code event} —— 领域事件模型、SPI 契约、本地线程池与消息队列事件管理</li>
 *   <li>{@code operation} —— 实体操作追踪与注册，用于领域事件归因</li>
 *   <li>{@code application} —— 应用服务基类、工作单元、命令执行器与事务性 Outbox</li>
 *   <li>{@code repository} —— 仓储契约、读模型聚合查询 / 投影与对账（含 {@code repository.query} / {@code repository.reconciliation}）</li>
 *   <li>{@code track} —— 变更追踪集合（TrackedList / TrackedMap）</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // 1. 定义聚合根：实现 brokenRuleRegistry / operationRegistry 两个抽象方法
 * public class Order extends AggregateRoot<Long> {
 *     private String pin;
 *     private BigDecimal totalPrice;
 *
 *     public void payment() {
 *         this.recordOperation(OrderOperation.PAY);
 *         this.collectEvent(new OrderPayedEvent(this));
 *     }
 *
 *     @Override
 *     protected BrokenRuleRegistry brokenRuleRegistry() {
 *         return OrderBrokenRuleRegistry.INSTANCE;
 *     }
 *
 *     @Override
 *     protected OperationRegistry operationRegistry() {
 *         return OrderOperation.REGISTRY;
 *     }
 * }
 *
 * // 2. 定义业务规则（校验项接收新模型 + 旧模型双参数）
 * public class OrderEntityRule extends EntityRule<Order> {
 *     public OrderEntityRule() {
 *         this.addRule(EntityRule.of(model ->
 *                         model.getTotalPrice() != null
 *                                 && model.getTotalPrice().compareTo(BigDecimal.ZERO) > 0
 *                                 ? RuleCheckResult.pass()
 *                                 : RuleCheckResult.fail(),
 *                 OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR));
 *     }
 * }
 *
 * // 3. 校验并收集领域事件，交由事件管理器发布
 * Order order = new Order();
 * if (!order.satisfiesRule(new OrderEntityRule())) {
 *     order.throwBrokenRuleException();
 * }
 * order.payment();
 * eventManager.publishList(order.getDomainEvents());
 * order.clearWorkUnitState();
 * }</pre>
 *
 * @see io.pragmatic.ddd.base.AggregateRoot
 * @see io.pragmatic.ddd.rules.EntityRule
 * @see io.pragmatic.ddd.event.BaseDomainEvent
 * @author wizard-lee
 */
package io.pragmatic.ddd;
