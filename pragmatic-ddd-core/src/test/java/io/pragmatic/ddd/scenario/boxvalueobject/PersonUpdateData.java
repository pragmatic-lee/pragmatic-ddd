package io.pragmatic.ddd.scenario.boxvalueobject;

import lombok.Data;

@Data
public class PersonUpdateData {
    private String name;
    private String age;
    private String email;
    private String phone;
}
