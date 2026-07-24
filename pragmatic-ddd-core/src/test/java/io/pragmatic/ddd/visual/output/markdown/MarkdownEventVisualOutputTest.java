package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import io.pragmatic.ddd.visual.event.EventDescriptor;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;

public class MarkdownEventVisualOutputTest {

    @Test
    public void outputTest() {


        MarkdownEventVisualOutput markDownEventVisualOutput = new MarkdownEventVisualOutput();


        List<EventDescriptor> eventDescriptors = this.mockData();
        EntityDescriptor entityDescriptor = this.mockData2();

        String output = markDownEventVisualOutput.output(eventDescriptors,entityDescriptor);

        System.out.println(output);

    }

    public EntityDescriptor mockData2() {
        String jsonString = "{\n" +
                "        \"clsName\": \"Order\",\n" +
                "        \"description\": \"销售订单\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          {\n" +
                "            \"description\": \"构造\",\n" +
                "            \"methodName\": \"Constructor\",\n" +
                "            \"triggerEvents\": [\n" +
                "              \"OrderSubmittedEvent\"\n" +
                "            ]\n" +
                "          },\n" +
                "          {\n" +
                "            \"description\": \"\",\n" +
                "            \"methodName\": \"payResult\",\n" +
                "            \"triggerEvents\": [\n" +
                "              \"OrderPayedEvent\"\n" +
                "            ]\n" +
                "          },\n" +
                "          {\n" +
                "            \"description\": \"\",\n" +
                "            \"methodName\": \"shipResult\",\n" +
                "            \"triggerEvents\": [\n" +
                "              \"OrderShippingEvent\"\n" +
                "            ]\n" +
                "          },\n" +
                "          {\n" +
                "            \"description\": \"\",\n" +
                "            \"methodName\": \"cancel\",\n" +
                "            \"triggerEvents\": [\n" +
                "              \"OrderCanceledEvent\"\n" +
                "            ]\n" +
                "          }\n" +
                "        ],\n" +
                "        \"fieldInfoList\": [\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单ID\",\n" +
                "            \"fieldName\": \"id\",\n" +
                "            \"type\": \"Long\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单状态\",\n" +
                "            \"fieldName\": \"orderStatus\",\n" +
                "            \"type\": \"OrderStatus\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单取消原因\",\n" +
                "            \"fieldName\": \"cancelReason\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单取消时间\",\n" +
                "            \"fieldName\": \"cancelTime\",\n" +
                "            \"type\": \"DateTime\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单创建时间\",\n" +
                "            \"fieldName\": \"createdTime\",\n" +
                "            \"type\": \"DateTime\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单支付信息\",\n" +
                "            \"fieldName\": \"payInfo\",\n" +
                "            \"type\": \"PayInfo\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单物流信息\",\n" +
                "            \"fieldName\": \"shippingInfo\",\n" +
                "            \"type\": \"ShippingInfo\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单总金额\",\n" +
                "            \"fieldName\": \"totalPrice\",\n" +
                "            \"type\": \"BigDecimal\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"订单收货地址\",\n" +
                "            \"fieldName\": \"receiveAddress\",\n" +
                "            \"type\": \"ReceiveAddress\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"支付状态1=等待支付 2=支付成功 3=支付超时\",\n" +
                "            \"fieldName\": \"payStatus\",\n" +
                "            \"type\": \"PayStatus\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"支付超时时间\",\n" +
                "            \"fieldName\": \"payTimeout\",\n" +
                "            \"type\": \"DateTime\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.Order\",\n" +
                "            \"collection\": true,\n" +
                "            \"description\": \"订单明细\",\n" +
                "            \"fieldName\": \"oderDetailItem\",\n" +
                "            \"type\": \"OderDetailItem\"\n" +
                "          }\n" +
                "        ],\n" +
                "        \"root\": true\n" +
                "      }";

        return JSON.parseObject(jsonString, EntityDescriptor.class);
    }

    public List<EventDescriptor> mockData() {
        String jsonString = "[\n" +
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

        return JSON.parseArray(jsonString, EventDescriptor.class);
    }
}
