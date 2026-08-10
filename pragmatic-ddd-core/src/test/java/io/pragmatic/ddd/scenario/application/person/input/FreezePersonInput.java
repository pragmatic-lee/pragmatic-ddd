package io.pragmatic.ddd.scenario.application.person.input;

import lombok.Data;

/**
 * 冻结人员入参。
 *
 * @author wizard-lee
 */
@Data
public class FreezePersonInput {

    private long id;

    private String reason;
}
