package io.pragmatic.ddd.scenario.application.person.factory;

import io.pragmatic.ddd.application.EntityFactory;
import io.pragmatic.ddd.scenario.application.person.input.CreatePersonInput;
import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.param.PersonInitData;

/**
 * 人员工厂：创建场景 Input 转领域参数并构造聚合根。
 *
 * @author wizard-lee
 */
public class PersonFactory implements EntityFactory<Person, CreatePersonInput> {

    @Override
    public Person create(CreatePersonInput input) {
        PersonInitData data = new PersonInitData();
        data.setId(input.getId());
        data.setName(input.getName());
        data.setAge(input.getAge());
        data.setEmail(input.getEmail());
        data.setPhone(input.getPhone());
        data.setStatus(input.getStatus());
        return new Person(data);
    }
}
