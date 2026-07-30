package io.pragmatic.ddd.base;

import lombok.Getter;

/**
 * 单条规则违反的值对象：名称、描述与可选的扩展数据。
 *
 * @author wizard-lee
 */
@Getter
public class BrokenRule {

    private final String name;
    private final String description;
    private final Object[] extraData;

    /** 创建不含扩展数据的规则违反。 */
    public BrokenRule(String name, String description) {
        this(name, description, null);
    }

    /** 创建含扩展数据的规则违反。 */
    public BrokenRule(String name, String description, Object[] extraData) {
        this.name = name;
        this.description = description;
        this.extraData = extraData;
    }
}
