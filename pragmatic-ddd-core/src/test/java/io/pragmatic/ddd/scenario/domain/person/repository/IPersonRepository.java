package io.pragmatic.ddd.scenario.domain.person.repository;

import io.pragmatic.ddd.repository.IRepository;
import io.pragmatic.ddd.scenario.domain.person.model.Person;

/**
 * 人员仓储接口（写模型持久化契约，不含实现）。
 *
 * @author wizard-lee
 */
public interface IPersonRepository extends IRepository<Long, Person> {
}
