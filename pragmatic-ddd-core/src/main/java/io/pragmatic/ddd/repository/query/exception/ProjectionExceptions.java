package io.pragmatic.ddd.repository.query.exception;

import java.util.function.Supplier;

/**
 * 读侧检索异常包装辅助，收敛 searcher 调用的样板处理，避免异常被重复嵌套。
 * 若步骤已抛出 {@link ProjectionException}，则原样传递；否则包装为对应具体异常并保留 cause。
 * 对齐 io.pragmatic.ddd.acl.AclExceptions / io.pragmatic.ddd.broadcast.BroadcastExceptions 的设计意图。
 *
 * @author wizard-lee
 */
public final class ProjectionExceptions {

    private ProjectionExceptions() {
    }

    /**
     * 检索执行阶段包装：通信/反序列化失败归为 {@link ProjectionRetrieveException}（可重试）。
     *
     * @param step  检索步骤
     * @param stage 阶段描述（用于异常文案）
     * @param <T>   步骤返回值类型
     * @return 步骤返回值
     */
    public static <T> T retrieve(Supplier<T> step, String stage) {
        try {
            return step.get();
        } catch (ProjectionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProjectionRetrieveException("投影检索失败 stage=" + stage, ex);
        }
    }

    /**
     * 条件翻译阶段包装：条件非法/不支持归为 {@link ProjectionConditionException}（不可重试）。
     *
     * @param step  翻译步骤
     * @param stage 阶段描述（用于异常文案）
     * @param <T>   步骤返回值类型
     * @return 步骤返回值
     */
    public static <T> T translate(Supplier<T> step, String stage) {
        try {
            return step.get();
        } catch (ProjectionException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProjectionConditionException("投影条件翻译失败 stage=" + stage, ex);
        }
    }
}
