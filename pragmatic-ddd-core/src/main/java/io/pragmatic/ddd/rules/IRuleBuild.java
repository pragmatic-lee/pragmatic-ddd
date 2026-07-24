package io.pragmatic.ddd.rules;

/**
 * @author lixiaojing10
 */
public interface IRuleBuild {
    default void init(){}
    void reset();
}
