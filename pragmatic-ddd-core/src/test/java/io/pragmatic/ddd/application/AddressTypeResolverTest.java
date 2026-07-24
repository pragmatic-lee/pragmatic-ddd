package io.pragmatic.ddd.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 演示 v3 方案：用 {@link FieldCalculator} + {@link FieldResolvers#from} 实现跨 DTO 复用，
 * 并把<b>实体</b>纳入计算上下文。
 *
 * <p><b>问题</b>：{@link CreateAddressCommand} 与 {@link UpdateAddressCommand} 都有
 * {@code addressType} 字段，判断逻辑完全相同（"1"→"1"、"2"→"2"、其它→null），
 * 但因 DTO 类型不同，旧写法被迫重复。此外<b>修改场景</b>往往要参考实体当前值
 * （命令未给的字段沿用旧值），旧写法只能在 Updater 里手动把实体旧值拼回命令。</p>
 *
 * <p><b>解法</b>：</p>
 * <ol>
 *   <li>把判断逻辑写成 {@link AddressTypeCalculator}
 *       （只认 {@code String} + 实体，不认 DTO）；</li>
 *   <li>用 {@link FieldResolvers#from(Class, Class, FieldCalculator, java.util.function.BiFunction)}
 *       把同一个 Calculator 适配到两个 DTO —— 差异仅在"如何取值"；</li>
 *   <li>修改场景的 extractor 把实体旧值透传给 Calculator，实现"命令未给则沿用旧值"。</li>
 * </ol>
 */
class AddressTypeResolverTest {

    // 计算逻辑只定义一次，创建 / 修改场景共用
    private final AddressTypeCalculator addressTypeCalculator = new AddressTypeCalculator();

    @Test
    void should_reuse_calculator_across_create_and_update() {
        // 创建场景：把 CreateAddressCommand 适配到 Calculator（无实体，entity 为 null）
        FieldResolver<CreateAddressCommand, Address, String> createResolver =
                FieldResolvers.from(CreateAddressCommand.class, Address.class, addressTypeCalculator,
                        (cmd, entity) -> cmd.getAddressType());

        // 修改场景：复用同一个 Calculator，只换 extractor
        FieldResolver<UpdateAddressCommand, Address, String> updateResolver =
                FieldResolvers.from(UpdateAddressCommand.class, Address.class, addressTypeCalculator,
                        (cmd, entity) -> cmd.getAddressType());

        CreateAddressCommand createCmd = new CreateAddressCommand();
        createCmd.setAddressType("1");
        assertEquals("1", createResolver.resolve(createCmd));   // 便捷重载，entity 内部为 null
        createCmd.setAddressType("3");
        assertNull(createResolver.resolve(createCmd));

        UpdateAddressCommand updateCmd = new UpdateAddressCommand();
        updateCmd.setAddressType("2");
        assertEquals("2", updateResolver.resolve(updateCmd, new Address()));
        updateCmd.setAddressType("3");
        assertNull(updateResolver.resolve(updateCmd, new Address()));
    }

    /**
     * v3 关键演示：修改场景命令未提供 addressType 时，Calculator 回退到实体当前值。
     * 旧方案做不到这一点——它根本拿不到实体，只能在 Updater 里手动拼。
     */
    @Test
    void update_should_fallback_to_entity_when_command_omits_field() {
        FieldResolver<UpdateAddressCommand, Address, String> updateResolver =
                FieldResolvers.from(UpdateAddressCommand.class, Address.class, addressTypeCalculator,
                        (cmd, entity) -> cmd.getAddressType());

        // 实体当前值为 "1"，命令未给 addressType（null）
        Address existing = new Address();
        existing.setAddressType("1");

        UpdateAddressCommand updateCmd = new UpdateAddressCommand();  // addressType 为 null
        assertEquals("1", updateResolver.resolve(updateCmd, existing));  // 沿用实体原值

        // 命令显式改为 "2"，覆盖实体原值
        updateCmd.setAddressType("2");
        assertEquals("2", updateResolver.resolve(updateCmd, existing));
    }

    /**
     * v3 的额外收益：Calculator 与 DTO 解耦，可单独、直接单元测试，
     * 无需构造任何命令对象。
     */
    @Test
    void calculator_can_be_unit_tested_alone() {
        assertEquals("1", addressTypeCalculator.calculate("1", null));
        assertEquals("2", addressTypeCalculator.calculate("2", null));
        assertNull(addressTypeCalculator.calculate("3", null));
        assertNull(addressTypeCalculator.calculate(null, null));

        // 实体回退逻辑也可直接单测
        Address existing = new Address();
        existing.setAddressType("1");
        assertEquals("1", addressTypeCalculator.calculate(null, existing));
    }
}
