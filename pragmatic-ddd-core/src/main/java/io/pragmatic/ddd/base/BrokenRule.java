package io.pragmatic.ddd.base;

import lombok.Getter;

@Getter
public class BrokenRule {

    private final String name;
    private final String description;
    private final Object[] extraData;

    public BrokenRule(String name, String description) {
        this(name, description, null);
    }

    public BrokenRule(String name, String description, Object[] extraData) {
        this.name = name;
        this.description = description;
        this.extraData = extraData;
    }
}
