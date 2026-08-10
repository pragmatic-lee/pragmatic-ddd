package io.pragmatic.ddd.scenario.application.person.input;

import io.pragmatic.ddd.scenario.domain.person.model.enums.Status;
import lombok.Data;

/**
 * 创建人员入参（业务语义，与协议无关）。
 *
 * @author wizard-lee
 */
@Data
public class CreatePersonInput {
    private long id;
    private String name;
    private String age;
    private String email;
    private String phone;
    private Status status;
}
