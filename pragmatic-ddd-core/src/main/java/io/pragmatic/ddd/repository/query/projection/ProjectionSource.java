package io.pragmatic.ddd.repository.query.projection;

import java.util.Objects;

/**
 * 投影源（读侧副本）的稳定标识，是读侧寻址的第一维度。
 * 一个源对应一份物理副本：ES 一个索引 = 一个源，Redis 一个键空间 = 一个源。
 * 源的 {@code id} 即副本标识（{@link io.pragmatic.ddd.repository.IReadModelReplica#replicaId()}），
 * 源自身即副本，读写与对账共用同一身份。
 *
 * @param id 源标识，全局唯一，非空
 * @author wizard-lee
 */
public record ProjectionSource(String id) {

    /** 紧凑构造器：仅做非空校验（record 不会自动保证组件非空）。 */
    public ProjectionSource {
        Objects.requireNonNull(id, "id");
    }

    /**
     * 由标识字符串构造源，语义同构造器但更贴合调用点风格。
     *
     * @param id 源标识
     * @return 源实例
     */
    public static ProjectionSource of(String id) {
        return new ProjectionSource(id);
    }

    @Override
    public String toString() {
        return "ProjectionSource{" + id + "}";
    }
}
