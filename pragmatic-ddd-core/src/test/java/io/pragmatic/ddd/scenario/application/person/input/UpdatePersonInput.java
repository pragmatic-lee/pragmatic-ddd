package io.pragmatic.ddd.scenario.application.person.input;

import lombok.Data;

/**
 * 修改人员入参（业务语义，与协议无关）。
 *
 * @author wizard-lee
 */
@Data
public class UpdatePersonInput {
    private long id;
    private String name;
    private String age;
    private String email;
    private String phone;
}
