package io.pragmatic.ddd.scenario.domain.person.param;

import io.pragmatic.ddd.scenario.domain.person.model.valueobject.Address;
import lombok.Data;

@Data
public class PersonUpdateData {
    private String name;
    private String age;
    private String email;
    private String phone;
    private Address address;
}
