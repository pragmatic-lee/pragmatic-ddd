package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.6：AbstractEntity（实体基类）单元测试，替代无效的 EntityBaseTest。
  * @author wizard-lee
 */
class AbstractEntityTest {

    static class SampleEntity extends AbstractEntity<Long> {
        SampleEntity(Long id) {
            setEntityId(id);
        }

        SampleEntity() {
        }
    }

    static class OtherEntity extends AbstractEntity<Long> {
        OtherEntity(Long id) {
            setEntityId(id);
        }
    }

    @Test
    void equals_bothIdNonNullAndEqual_isTrue() {
        assertThat(new SampleEntity(1L)).isEqualTo(new SampleEntity(1L));
        // 跨子类仅看 ID（固化现状契约：Order(1) equals User(1)）
        assertThat(new SampleEntity(1L).equals(new OtherEntity(1L))).isTrue();
    }

    @Test
    void equals_idNotEqual_orAnyNull_isFalse() {
        assertThat(new SampleEntity(1L)).isNotEqualTo(new SampleEntity(2L));
        assertThat(new SampleEntity(1L)).isNotEqualTo(new SampleEntity(null));
        assertThat(new SampleEntity(1L)).isNotEqualTo(new SampleEntity());
        assertThat(new SampleEntity()).isNotEqualTo(new SampleEntity());
    }

    @Test
    void equals_reflexive_andNullAndNonEntity() {
        SampleEntity e = new SampleEntity(1L);
        assertThat(e).isEqualTo(e);
        assertThat(e).isNotEqualTo(null);
        assertThat(e).isNotEqualTo("not an entity");
    }

    @Test
    void hashCode_idNonNull_equalsIdHashCode() {
        SampleEntity e = new SampleEntity(1L);
        assertThat(e.hashCode()).isEqualTo(Long.valueOf(1L).hashCode());
    }

    @Test
    void hashCode_idNull_noException_andStable() {
        SampleEntity e = new SampleEntity();
        assertThat(e.hashCode()).isEqualTo(e.hashCode());
    }

    @Test
    void markCreated_setsBothTimestamps() {
        SampleEntity e = new SampleEntity(1L);
        e.markCreated();
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
        assertThat(e.getCreatedAt()).isEqualTo(e.getUpdatedAt());
    }

    @Test
    void markModified_onlyUpdatesUpdatedAt() {
        SampleEntity e = new SampleEntity(1L);
        e.markCreated();
        LocalDateTime createdAt = e.getCreatedAt();
        e.markModified();
        assertThat(e.getCreatedAt()).isEqualTo(createdAt);
        assertThat(e.getUpdatedAt()).isNotNull();
    }

    @Test
    void entityDelete_defaultFalse_toggleWorks() {
        SampleEntity e = new SampleEntity(1L);
        assertThat(e.isEntityDelete()).isFalse();
        e.setEntityDelete(true);
        assertThat(e.isEntityDelete()).isTrue();
    }

    @Test
    void toString_containsClassNameAndId_nullSafe() {
        SampleEntity e = new SampleEntity(1L);
        assertThat(e.toString()).contains("SampleEntity").contains("1");
        SampleEntity empty = new SampleEntity();
        assertThat(empty.toString()).contains("id=null");
    }
}
