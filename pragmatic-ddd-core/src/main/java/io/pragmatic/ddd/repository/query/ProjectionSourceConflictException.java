package io.pragmatic.ddd.repository.query;

/**
 * 源冲突异常：注册或绑定时出现违反唯一性的情况。
 * 包括：源 id 重复、同一投影类被两个源占用、同一 (源, 条件族) 重复绑定不同检索器、
 * 同一 (源, 子投影) 重复绑定不同裁剪器。
 * 语义上属于"接线/配置错误"，应在启动时暴露，故归为不可重试。
 *
 * @author wizard-lee
 */
public class ProjectionSourceConflictException extends ProjectionException {

    public ProjectionSourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProjectionSourceConflictException(String message) {
        super(message);
    }
}
