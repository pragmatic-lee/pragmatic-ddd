package io.pragmatic.ddd.scenario.infrastructure.person.repository;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.repository.IPersonRepository;

/**
 * 人员仓储实现（壳子）。
 * 示例框架不连真实持久化，方法体为空实现；真实项目在此注入 Mapper / JPA 落地。
 *
 * @author wizard-lee
 */
public class PersonRepository implements IPersonRepository {

    @Override
    public void insert(Person aggregateRoot) {
        // 壳子：真实项目在此调用持久化中间件
    }

    @Override
    public void update(Person aggregateRoot) {
        // 壳子：真实项目在此调用持久化中间件
    }

    @Override
    public Person findById(Long id) {
        // 壳子：真实项目查库返回聚合根
        return null;
    }

    @Override
    public void remove(Person aggregateRoot) {
        // 壳子：真实项目在此物理/逻辑删除
    }
}
