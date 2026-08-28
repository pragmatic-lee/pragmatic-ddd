package io.pragmatic.ddd.repository.query;

/**
 * 检索器未找到异常：注册表中不存在 (criteriaType, projectionType) 对应的 searcher。
 * 语义上属于"接线/配置缺失"，应由集成模块在启动时确保登记，故归为不可重试。
 *
 * @author wizard-lee
 */
public class ProjectionSearcherNotFoundException extends ProjectionException {

    public ProjectionSearcherNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionSearcherNotFoundException(String message) {
        super(message);
    }
}
