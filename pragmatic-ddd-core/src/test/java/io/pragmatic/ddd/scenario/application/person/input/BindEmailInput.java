package io.pragmatic.ddd.scenario.application.person.input;

import lombok.Data;

/**
 * 绑定邮箱入参。
 *
 * @author wizard-lee
 */
@Data
public class BindEmailInput {

    private long id;

    private String email;
}
