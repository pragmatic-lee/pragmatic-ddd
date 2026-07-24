package io.pragmatic.ddd.application;

/**
 * 字段解析器 —— 从 Command DTO + 实体 中计算实体字段值。
 *
 * <p>定位：应用服务层的字段级计算入口。每个需要计算的实体字段
 * 对应一个 FieldResolver，将计算逻辑集中在一处，多个场景复用。</p>
 *
 * <p>v3 增强：计算上下文同时包含 Command DTO 与<b>实体</b>。这是因为字段计算本质是在算
 * "实体上的字段"，尤其是修改场景：新值常常要参考实体当前状态
 * （保留命令未改的字段、基于旧值算差值、校验既有状态等）。</p>
 *
 * <p>创建场景实体尚不存在，调用便捷重载 {@link #resolve(Object)}（entity 内部置 null）；
 * 修改场景实体已从仓储加载，调用 {@link #resolve(Object, Object)} 显式传入。</p>
 *
 * <p><b>简单字段不需要 FieldResolver</b>，直接赋值即可：
 * {@code param.setPin(cmd.getPin())}</p>
 *
 * <p><b>典型用法：</b></p>
 * <pre>{@code
 *   // 定义：address 字段的计算器（创建 / 修改共用，可读取实体）
 *   public class AddressResolver implements
 *           FieldResolver<CreateOrderCommand, Order, String>,
 *           FieldResolver<ChangeAddressCommand, Order, String> {
 *
 *       public String resolve(CreateOrderCommand cmd, Order order) {
 *           return String.join(" ", cmd.getProvince(), cmd.getCity(), cmd.getDistrict());
 *       }
 *
 *       public String resolve(ChangeAddressCommand cmd, Order order) {
 *           // 修改场景：命令没给 detail 时回退到实体原值
 *           String detail = cmd.getDetail() != null ? cmd.getDetail() : order.getAddressDetail();
 *           return String.join(" ", cmd.getProvince(), cmd.getCity(), cmd.getDistrict(), detail);
 *       }
 *   }
 *
 *   // 使用：先算后赋
 *   String address = addressResolver.resolve(cmd, order);
 *   param.setAddress(address);
 * }</pre>
 *
 * @param <C> Command DTO 类型（输入：从哪里取数据）
 * @param <E> 实体类型（聚合根；创建场景为 null）
 * @param <R> 字段值类型（输出：计算出的结果）
 * @author Li XiaoJing
 * @since 2.2.0
 */
@FunctionalInterface
public interface FieldResolver<C, E, R> {

    /**
     * 从 Command DTO + 实体 计算出该字段的值。
     * <p>此方法内部可以包含：</p>
     * <ul>
     *   <li>领域服务调用</li>
     *   <li>多字段结合（命令 + 实体）</li>
     *   <li>格式转换</li>
     *   <li>默认值处理（含回退到实体原值）</li>
     * </ul>
     * 调用方拿到结果后直接赋值，不再做任何计算。
     *
     * @param command Command DTO
     * @param entity  实体实例；创建场景传 null
     * @return 计算后的字段值
     */
    R resolve(C command, E entity);

    /**
     * 创建场景便捷方法：实体尚不存在，entity 置为 null。
     *
     * <p>Factory 内部调用此方法，无需关心实体维度。</p>
     *
     * @param command Command DTO
     * @return 计算后的字段值
     */
    default R resolve(C command) {
        return resolve(command, null);
    }
}
