package io.pragmatic.ddd.broadcast;

/**
 * 对外广播异常工具类，收敛 try-catch 与包装逻辑，并避免异常被重复嵌套。
 * 对齐 io.pragmatic.ddd.acl.AclExceptions 的设计意图。
 *
 * @author wizard-lee
 */
public final class BroadcastExceptions {

    private BroadcastExceptions() {
    }

    /**
     * 将发送阶段异常包装为 BroadcastSendException；若已是该类型则原样返回。
     *
     * @param topic 发送目标 topic
     * @param e     原始异常
     * @return 广播发送异常
     */
    public static BroadcastSendException wrapSend(String topic, Throwable e) {
        if (e instanceof BroadcastSendException b) {
            return b;
        }
        return new BroadcastSendException("广播发送失败 topic=" + topic, e);
    }

    /**
     * 将信封处理阶段异常包装为 BroadcastEnvelopeException；若已是该类型则原样返回。
     *
     * @param stage 处理阶段描述（如 serialize / wrap）
     * @param e     原始异常
     * @return 信封处理异常
     */
    public static BroadcastEnvelopeException wrapEnvelope(String stage, Throwable e) {
        if (e instanceof BroadcastEnvelopeException b) {
            return b;
        }
        return new BroadcastEnvelopeException("信封处理失败 stage=" + stage, e);
    }
}
