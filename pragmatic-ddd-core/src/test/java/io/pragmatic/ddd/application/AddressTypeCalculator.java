package io.pragmatic.ddd.application;

/**
 * 地址类型判断逻辑 —— 与任何 DTO 解耦、但可读取实体的纯计算单元。
 *
 * <p>这是 v3 方案的核心价值所在：把"计算逻辑"从"数据提取"中剥离出来。
 * 它只接收原始字符串（地址类型编码）与实体，返回标准化结果，
 * 完全不知道数据来自创建命令还是修改命令。</p>
 *
 * <p>v3 增强点：当命令未提供地址类型时（修改场景的"部分更新"），
 * 直接回退到实体的当前值 {@code entity.getAddressType()}，
 * 无需在 Updater 里手动把旧值拼回命令。</p>
 *
 * <p>因为与 DTO 解耦：</p>
 * <ul>
 *   <li>创建、修改场景可以共用同一个实例（见 {@link AddressTypeResolverTest}）；</li>
 *   <li>只需针对它写一份单元测试，无需构造任何命令对象。</li>
 * </ul>
 */
public class AddressTypeCalculator implements FieldCalculator<String, Address, String> {

    @Override
    public String calculate(String addressType, Address entity) {
        // 命令给了就用命令的；命令没给（部分更新）就沿用实体原值
        String source = addressType != null ? addressType
                : (entity != null ? entity.getAddressType() : null);
        if ("1".equals(source)) return "1";
        if ("2".equals(source)) return "2";
        return null;
    }
}
