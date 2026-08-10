package io.pragmatic.ddd.scenario.application.person.input;

import lombok.Data;

/**
 * 归属部门变更入参。
 *
 * @author wizard-lee
 */
@Data
public class AssignDeptInput {

    private long id;

    private Long departmentId;

    private String position;
}
