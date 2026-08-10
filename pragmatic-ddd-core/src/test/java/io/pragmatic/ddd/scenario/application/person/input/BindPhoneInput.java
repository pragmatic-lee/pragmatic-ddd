package io.pragmatic.ddd.scenario.application.person.input;

import lombok.Data;

/**
 * 绑定手机号入参。
 *
 * @author wizard-lee
 */
@Data
public class BindPhoneInput {

    private long id;

    private String phone;
}
