package io.pragmatic.ddd.base;

import lombok.Getter;

@Getter
public class BrokenRule {

    private final String name;
    private final String description;
    private final String property;
    private final String alias;
    private final Object[] extraData;


    public BrokenRule(String name, String description) {
        this(name, description, "");
    }

    public BrokenRule(String name, String description, String property) {
        this(name, description, property, "", null);
    }

    public BrokenRule(String name, String description, String property, String alias, Object[] extraData) {
        this.name = name;
        this.description = description;
        this.property = property;
        this.alias = alias == null || alias.isEmpty() ? property : alias;
        this.extraData = extraData;
    }
}
