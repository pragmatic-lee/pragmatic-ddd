package io.pragmatic.ddd.base;

import java.util.HashMap;
import java.util.Map;

/**
 * 领域事件订阅者 Key 抽象基类。
 * 子类通过 populateKeys() 声明订阅所需的 Key 描述与合并策略（KeySetting），供领域事件订阅配置使用。
 *
 * @author wizard-lee
 */
public abstract class AbstractSubscriberKey {
    private final HashMap<String, KeySetting> keys;

    /** 构造时初始化 Key 集合并调用子类填充逻辑。 */
    protected AbstractSubscriberKey() {
        this.keys = new HashMap<>();
        this.populateKeys();
    }

    /** 子类实现：填充本订阅者关心的 Key 集合。 */
    protected abstract void populateKeys();

    /** 返回本订阅者声明的全部 Key。 */
    public Map<String, KeySetting> getKeys() {
        return keys;
    }

    /** 按名称获取单个 Key 的配置（KeySetting）。 */
    public KeySetting getKeyInfo(String key) {
        return keys.get(key);
    }

    /** 以描述构建一个 KeySetting（默认不合并同名 Key）。 */
    public static KeySetting buildKeySetting(String description) {
        return buildKeySetting(false, description, null);
    }

    /** 以描述与合并策略构建一个 KeySetting。 */
    public static KeySetting buildKeySetting(String description, boolean mergeSameKey) {
        return buildKeySetting(mergeSameKey, description, null);
    }

    /** 以合并策略、描述与自定义信息构建一个 KeySetting。 */
    public static KeySetting buildKeySetting(boolean mergeSameKey, String description, Object customInfo) {
        return new KeySetting(mergeSameKey, description, customInfo);
    }

    /** 单个订阅 Key 的配置：是否合并同名 Key、描述与自定义信息。 */
    public static class KeySetting {
        private boolean mergeSameKey;
        private String description;
        private Object customInfo;

        /** 以合并策略、描述与自定义信息构造。 */
        public KeySetting(boolean mergeSameKey, String description, Object customInfo) {
            this.mergeSameKey = mergeSameKey;
            this.description = description;
            this.customInfo = customInfo;
        }

        /** 是否合并同名 Key。 */
        public boolean isMergeSameKey() {
            return mergeSameKey;
        }

        /** 设置是否合并同名 Key。 */
        public void setMergeSameKey(boolean mergeSameKey) {
            this.mergeSameKey = mergeSameKey;
        }

        /** 返回描述。 */
        public String getDescription() {
            return description;
        }

        /** 设置描述。 */
        public void setDescription(String description) {
            this.description = description;
        }

        /** 返回自定义信息。 */
        public Object getCustomInfo() {
            return customInfo;
        }

        /** 设置自定义信息。 */
        public void setCustomInfo(Object customInfo) {
            this.customInfo = customInfo;
        }
    }
}
