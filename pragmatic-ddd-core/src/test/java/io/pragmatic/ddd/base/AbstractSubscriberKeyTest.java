package io.pragmatic.ddd.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对应设计文档阶段 6.12：AbstractSubscriberKey 模板方法与 KeySetting 契约测试。
 */
class AbstractSubscriberKeyTest {

    static class SampleKey extends AbstractSubscriberKey {
        @Override
        protected void populateKeys() {
            getKeys().put("k1", buildKeySetting("desc1"));
            getKeys().put("k2", buildKeySetting("desc2", true));
            getKeys().put("k3", buildKeySetting(true, "desc3", "info"));
        }
    }

    @Test
    void populateKeys_calledOnConstruct() {
        SampleKey key = new SampleKey();
        assertThat(key.getKeys()).containsKeys("k1", "k2", "k3");
        assertThat(key.getKeys()).hasSize(3);
    }

    @Test
    void getKeyInfo_hitAndMiss() {
        SampleKey key = new SampleKey();
        assertThat(key.getKeyInfo("k1")).isNotNull();
        assertThat(key.getKeyInfo("missing")).isNull();
    }

    @Test
    void buildKeySetting_overloads_defaults() {
        AbstractSubscriberKey.KeySetting s1 = AbstractSubscriberKey.buildKeySetting("d");
        assertThat(s1.isMergeSameKey()).isFalse();
        assertThat(s1.getDescription()).isEqualTo("d");
        assertThat(s1.getCustomInfo()).isNull();

        AbstractSubscriberKey.KeySetting s2 = AbstractSubscriberKey.buildKeySetting("d", true);
        assertThat(s2.isMergeSameKey()).isTrue();
        assertThat(s2.getCustomInfo()).isNull();

        AbstractSubscriberKey.KeySetting s3 = AbstractSubscriberKey.buildKeySetting(true, "d", "info");
        assertThat(s3.isMergeSameKey()).isTrue();
        assertThat(s3.getDescription()).isEqualTo("d");
        assertThat(s3.getCustomInfo()).isEqualTo("info");
    }

    @Test
    void keySetting_setters() {
        AbstractSubscriberKey.KeySetting s = AbstractSubscriberKey.buildKeySetting("d");
        s.setMergeSameKey(true);
        s.setDescription("newDesc");
        s.setCustomInfo("newInfo");
        assertThat(s.isMergeSameKey()).isTrue();
        assertThat(s.getDescription()).isEqualTo("newDesc");
        assertThat(s.getCustomInfo()).isEqualTo("newInfo");
    }
}
