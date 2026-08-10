package io.pragmatic.ddd.scenario.domain.person.param;

import io.pragmatic.ddd.scenario.domain.person.model.enums.Status;
import io.pragmatic.ddd.scenario.domain.person.model.valueobject.Address;
import lombok.Data;

@Data
public class PersonInitData {

    private Long id;
    private String name;
    private String age;
    private String email;
    private String phone;
    private Status status;
    private Address address;
}
