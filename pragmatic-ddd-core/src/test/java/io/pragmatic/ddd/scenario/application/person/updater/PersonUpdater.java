package io.pragmatic.ddd.scenario.application.person.updater;

import io.pragmatic.ddd.application.EntityUpdater;
import io.pragmatic.ddd.scenario.application.person.input.UpdatePersonInput;
import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.param.PersonUpdateData;

/**
 * 人员修改器：修改场景 Input 转领域参数并驱动聚合根变更。
 *
 * @author wizard-lee
 */
public class PersonUpdater implements EntityUpdater<Person, UpdatePersonInput> {

    @Override
    public void apply(Person aggregateRoot, UpdatePersonInput command) {
        PersonUpdateData data = new PersonUpdateData();
        data.setName(command.getName());
        data.setAge(command.getAge());
        data.setEmail(command.getEmail());
        data.setPhone(command.getPhone());
        aggregateRoot.update(data);
    }
}
