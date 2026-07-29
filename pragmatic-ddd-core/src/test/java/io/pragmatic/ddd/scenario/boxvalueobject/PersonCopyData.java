package io.pragmatic.ddd.scenario.boxvalueobject;

import io.pragmatic.ddd.scenario.entity.enums.Status;
import lombok.Data;

@Data
public class PersonCopyData {
    private Long id;
    private String name;
    private String age;
    private String email;
    private String phone;
    private Status status;
}
