package io.pragmatic.ddd.base.test2.boxvalueobject;

import io.pragmatic.ddd.base.test2.entity.enums.Status;
import lombok.Data;

@Data
public class PersonInitData {

    private Long id;
    private String name;
    private String age;
    private String email;
    private String phone;
    private Status status;
}
