package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.event.EventDescriptor;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;

public class MarkdownEventSubscriberVisualOutputTest {

    @Test
    public void outputTest() {

        List<EventDescriptor> eventDescriptors = mockData();

        MarkdownEventSubscriberVisualOutput markDownEventSubscriberVisualOutput = new MarkdownEventSubscriberVisualOutput();

        String output = markDownEventSubscriberVisualOutput.output(eventDescriptors);

        System.out.println(output);
    }

    public List<EventDescriptor> mockData(){
        String jsonString ="[\n" +
                "      {\n" +
                "        \"eventDescription\": \"订单已发货事件\",\n" +
                "        \"eventName\": \"OrderShippingEvent\",\n" +
                "        \"subscriberDescriptorList\": [\n" +
                "          \n" +
                "        ]\n" +
                "      },\n" +
                "      {\n" +
                "        \"eventDescription\": \"订单已提交建事件\",\n" +
                "        \"eventName\": \"OrderSubmittedEvent\",\n" +
                "        \"subscriberDescriptorList\": [\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"_root_\",\n" +
                "            \"subscriberDescription\": \"写Es异构数据\",\n" +
                "            \"subscriberKey\": \"WRITE_ES\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"_root_\",\n" +
                "            \"subscriberDescription\": \"写高速缓存\",\n" +
                "            \"subscriberKey\": \"WRITE_CACHE\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"WRITE_CACHE\",\n" +
                "            \"subscriberDescription\": \"发送订单提交消息\",\n" +
                "            \"subscriberKey\": \"ORDER_SUBMIT_MESSAGE\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      {\n" +
                "        \"eventDescription\": \"订单已支付事件\",\n" +
                "        \"eventName\": \"OrderPayedEvent\",\n" +
                "        \"subscriberDescriptorList\": [\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"_root_\",\n" +
                "            \"subscriberDescription\": \"写Es异构数据\",\n" +
                "            \"subscriberKey\": \"WRITE_ES\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"_root_\",\n" +
                "            \"subscriberDescription\": \"写高速缓存\",\n" +
                "            \"subscriberKey\": \"WRITE_CACHE\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      {\n" +
                "        \"eventDescription\": \"订单发货\",\n" +
                "        \"eventName\": \"OrderCanceledEvent\",\n" +
                "        \"subscriberDescriptorList\": [\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"_root_\",\n" +
                "            \"subscriberDescription\": \"写Es异构数据\",\n" +
                "            \"subscriberKey\": \"WRITE_ES\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"dependsOnSubscriber\": \"_root_\",\n" +
                "            \"subscriberDescription\": \"写高速缓存\",\n" +
                "            \"subscriberKey\": \"WRITE_CACHE\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    ]";

        return JSON.parseArray(jsonString,EventDescriptor.class);
    }
}
