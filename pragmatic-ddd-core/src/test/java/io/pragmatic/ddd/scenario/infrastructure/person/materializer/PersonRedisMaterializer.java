package io.pragmatic.ddd.scenario.infrastructure.person.materializer;

import io.pragmatic.ddd.repository.query.AbstractAggregateProjector;
import io.pragmatic.ddd.repository.query.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.IAggregateProjection;
import io.pragmatic.ddd.repository.query.ProjectionSource;
import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;

/**
 * Redis 投影源（壳子）。
 * 示例框架不连真实 Redis，写方法体为空实现；真实项目在此写/删 Redis 副本并登记到 Registry。
 * 以「源」为中心聚合写（materialize / purge）与读（检索器由子类 bind），
 * 寻址串 redis:person 由源标识承载，写读共享同一份副本地址。
 *
 * @author wizard-lee
 */
public final class PersonRedisMaterializer extends AbstractProjectionSource<Person, PersonProjection> {

    public PersonRedisMaterializer() {
        super(ProjectionSource.of("redis:person"), Person.class, PersonProjection.class, new ShellProjector(), null);
    }

    @Override
    public void materialize(IAggregateProjection projection, long version) {
        // 壳子：真实项目在此写/更新 Redis 副本
    }

    @Override
    public void purge(Object aggregateId) {
        // 壳子：真实项目在此清理 Redis 残留条目
    }

    /** 壳子投影器：不真正投影，仅满足源构造约束。 */
    private static final class ShellProjector extends AbstractAggregateProjector<Person, PersonProjection> {

        private ShellProjector() {
            super(PersonProjection.class);
        }

        @Override
        public PersonProjection project(Person aggregateRoot) {
            return null;
        }
    }
}
