package io.pragmatic.ddd.repository.query.fixture;

import io.pragmatic.ddd.repository.query.IAggregateProjection;

/**
 * 投影测试用的读模型投影（实现 IAggregateProjection 标记接口）。
 */
public final class StubProjection implements IAggregateProjection {

    private final Long id;

    private final String name;

    public StubProjection(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }
}
