package io.pragmatic.ddd.repository.query.exception;

/**
 * 条件不可检索异常：业务条件无法翻译为具体存储的检索请求
 * （如条件字段在某存储无对应索引、或 searcher 未支持该条件子类）。
 * 属于调用方输入问题，归为不可重试。
 *
 * @author wizard-lee
 */
public class ProjectionConditionException extends ProjectionException {

    public ProjectionConditionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionConditionException(String message) {
        super(message);
    }
}
