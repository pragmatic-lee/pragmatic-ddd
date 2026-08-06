package io.pragmatic.ddd.config.feature;

/**
 * 特性开关的生效状态。
 *
 * @author wizard-lee
 */
public enum ToggleState {

    /** 关闭：未命中任何放量范围时返回 false。 */
    OFF,

    /** 灰度：仅命中灰度策略（指定人/账号/条件）时返回 true。 */
    ROLLOUT,

    /** 全量开启：对任何调用均返回 true。 */
    ON
}
