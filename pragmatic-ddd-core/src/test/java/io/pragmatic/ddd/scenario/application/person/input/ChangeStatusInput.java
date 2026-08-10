package io.pragmatic.ddd.scenario.application.person.input;

import io.pragmatic.ddd.scenario.domain.person.model.enums.Status;
import lombok.Data;

/**
 * 状态变更入参。
 *
 * @author wizard-lee
 */
@Data
public class ChangeStatusInput {

    private long id;

    private Status status;

    private String reason;
}
