package io.pragmatic.ddd.scenario.application.person.input;

import lombok.Data;

import java.util.List;

/**
 * 打标签入参。
 *
 * @author wizard-lee
 */
@Data
public class TagPersonInput {

    private long id;

    private List<String> tags;
}
