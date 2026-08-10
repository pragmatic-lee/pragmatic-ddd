package io.pragmatic.ddd.scenario.application.person.input;

import io.pragmatic.ddd.scenario.domain.person.model.enums.Status;
import lombok.Data;

/**
 * 修改人员状态入参（业务语义，与协议无关）。
 *
 * @author wizard-lee
 */
@Data
public class UpdatePersonStatusInput {
    private long id;
    private Status status;
}
