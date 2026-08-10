package io.pragmatic.ddd.scenario.application.person.subscriber;

/**
 * 人员场景订阅者常量，声明各订阅者执行场景的 code。
 *
 * @author wizard-lee
 */
public final class PersonSubscriberRegistry {

    private PersonSubscriberRegistry() {}

    public static final String UPDATE_SCORE = "updateScore";
    public static final String UPDATE_GRADE = "updateGrade";
}
