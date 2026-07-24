package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 实体更新器 —— 从 Command DTO 计算变更字段，调用实体业务方法完成修改。
 *
 * <p>定位：与 {@link EntityFactory} 对称，前者处理"创建"，后者处理"修改"。
 * 两者遵循同一个"先算后赋"原则。</p>
 *
 * <p><b>核心约定：</b></p>
 * <ol>
 *   <li>应用服务从仓储加载已有实体</li>
 *   <li>通过 FieldResolver 从 Command DTO 计算出各字段的新值</li>
 *   <li>将计算结果封装为 IParamObject，调用实体的业务方法</li>
 *   <li>实体内部纯赋值，不夹杂计算逻辑</li>
 * </ol>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   public class OrderUpdater implements EntityUpdater<Order, ChangeAddressCommand> {
 *
 *       // 第三个类型参数是实体；修改场景把已加载的 order 一起传入
 *       private final FieldResolver<ChangeAddressCommand, Order, String> addressResolver;
 *
 *       public void apply(Order order, ChangeAddressCommand cmd) {
 *           // ===== 阶段 1：计算（实体随命令一起进入计算上下文） =====
 *           String address = addressResolver.resolve(cmd, order);
 *
 *           // ===== 阶段 2：赋值（通过 IParamObject 传入实体业务方法） =====
 *           OrderUpdateAddressParam param = new OrderUpdateAddressParam();
 *           param.setAddress(address);
 *           order.changeAddress(param);
 *       }
 *   }
 * }</pre>
 *
 * @param <T> 聚合根类型
 * @param <C> Command DTO 类型
 * @author Li XiaoJing
 * @since 2.2.0
 */
public interface EntityUpdater<T extends AggregateRoot<?>, C> {

    /**
     * 对已有实体应用变更。
     *
     * <p>实现规范：</p>
     * <ul>
     *   <li>上方通过 FieldResolver 完成计算（与 Factory 共享 Resolver）</li>
     *   <li>下方组装 IParamObject，调用实体的业务方法</li>
     *   <li>实体内部纯赋值，无任何计算或判断逻辑</li>
     * </ul>
     *
     * @param aggregateRoot 从仓储加载的已有实体（非 null）
     * @param command       Command DTO
     */
    void apply(T aggregateRoot, C command);
}
