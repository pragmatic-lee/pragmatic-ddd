package io.pragmatic.ddd.event;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import io.pragmatic.ddd.base.fixture.SampleEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异构数据同步事件契约测试：验证同步动作默认值与显式赋值、FieldBased 序列化往返、
 * 以及与普通领域事件的类型判别。
 *
 * @author wizard-lee
 */
@DisplayName("IDataSyncEvent 契约")
class DataSyncEventContractTest {

    @Test
    void noExplicitAction_defaultIsUpsert() {
        SampleDataSyncEvent event = new SampleDataSyncEvent();

        assertThat(event.getDataSyncAction()).isEqualTo(DataSyncAction.UPSERT);
    }

    @Test
    void explicitPurge_actionIsPurge() {
        SampleDataSyncEvent event = new SampleDataSyncEvent("1", DataSyncAction.PURGE);

        assertThat(event.getDataSyncAction()).isEqualTo(DataSyncAction.PURGE);
        assertThat(event.getEntityId()).isEqualTo("1");
    }

    @Test
    void fieldBasedRoundTrip_keepsEntityIdAndAction() {
        SampleDataSyncEvent event = new SampleDataSyncEvent("42", DataSyncAction.PURGE);

        String json = JSON.toJSONString(event, JSONWriter.Feature.FieldBased);
        SampleDataSyncEvent parsed = JSON.parseObject(json, SampleDataSyncEvent.class, JSONReader.Feature.FieldBased);

        assertThat(parsed.getEntityId()).isEqualTo("42");
        assertThat(parsed.getDataSyncAction()).isEqualTo(DataSyncAction.PURGE);
        assertThat(parsed.getEventId()).isEqualTo(event.getEventId());
    }

    @Test
    void markerDistinguishesFromPlainDomainEvent() {
        SampleDataSyncEvent syncEvent = new SampleDataSyncEvent("1", DataSyncAction.UPSERT);
        SampleEvent plainEvent = new SampleEvent("1");

        assertThat(syncEvent).isInstanceOf(IDataSyncEvent.class);
        assertThat(plainEvent).isNotInstanceOf(IDataSyncEvent.class);
    }

    /** 最小同步事件夹具：只验证契约，不引入业务语义。 */
    static class SampleDataSyncEvent extends BaseDataSyncEvent {

        SampleDataSyncEvent(String entityId, DataSyncAction dataSyncAction) {
            super(entityId, dataSyncAction);
        }

        /** Fastjson2 Feature.FieldBased 反序列化入口 */
        SampleDataSyncEvent() {
            super();
        }
    }
}
