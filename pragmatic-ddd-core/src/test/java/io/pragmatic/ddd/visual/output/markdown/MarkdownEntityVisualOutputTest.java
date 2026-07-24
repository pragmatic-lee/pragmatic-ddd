package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.entity.EntityDescriptor;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;

public class MarkdownEntityVisualOutputTest {

    @Test
    public void outPutTest() {

        List<EntityDescriptor> entityDescriptors = this.mockData();

        MarkdownEntityVisualOutput markDownEntityVisualOutput = new MarkdownEntityVisualOutput();
        String output = markDownEntityVisualOutput.output(entityDescriptors);

        System.out.println(output);
    }

    private List<EntityDescriptor> mockData() {

        String jsonString = "[\n" +
                "      {\n" +
                "        \"clsName\": \"OderDetailItem\",\n" +
                "        \"description\": \"销售订单项\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          \n" +
                "        ],\n" +
                "        \"fieldInfoList\": [\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.OderDetailItem\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"商品单价\",\n" +
                "            \"fieldName\": \"price\",\n" +
                "            \"type\": \"BigDecimal\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.OderDetailItem\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"商品单位\",\n" +
                "            \"fieldName\": \"unit\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.OderDetailItem\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"购买商品数量\",\n" +
                "            \"fieldName\": \"count\",\n" +
                "            \"type\": \"BigDecimal\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.OderDetailItem\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"商品skuID\",\n" +
                "            \"fieldName\": \"skuId\",\n" +
                "            \"type\": \"Long\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.OderDetailItem\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"商品名称\",\n" +
                "            \"fieldName\": \"skuName\",\n" +
                "            \"type\": \"String\"\n" +
                "          }\n" +
                "        ],\n" +
                "        \"root\": false\n" +
                "      },\n" +
                "      {\n" +
                "        \"clsName\": \"ShippingInfo\",\n" +
                "        \"description\": \"订单物流信息\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          \n" +
                "        ],\n" +
                "        \"fieldInfoList\": [\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ShippingInfo\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"物流公司\",\n" +
                "            \"fieldName\": \"shipName\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ShippingInfo\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"快递单号\",\n" +
                "            \"fieldName\": \"shipCode\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ShippingInfo\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"发货状态 0=等待发货 1=已经发货\",\n" +
                "            \"fieldName\": \"shipStatus\",\n" +
                "            \"type\": \"ShipStatus\"\n" +
                "          }\n" +
                "        ],\n" +
                "        \"root\": false\n" +
                "      },\n" +
                "      {\n" +
                "        \"clsName\": \"PayInfo\",\n" +
                "        \"description\": \"销售订单支付信息\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          \n" +
                "        ],\n" +
                "        \"fieldInfoList\": [\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.PayInfo\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"支付时间\",\n" +
                "            \"fieldName\": \"payTime\",\n" +
                "            \"type\": \"DateTime\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.PayInfo\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"支付流水号\",\n" +
                "            \"fieldName\": \"payNo\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.PayInfo\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"支付方式0=微信 1=支付宝\",\n" +
                "            \"fieldName\": \"payType\",\n" +
                "            \"type\": \"PayType\"\n" +
                "          }\n" +
                "        ],\n" +
                "        \"root\": false\n" +
                "      },\n" +
                "      {\n" +
                "        \"clsName\": \"Point\",\n" +
                "        \"description\": \"收货地址经纬度\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          \n" +
                "        ],\n" +
                "        \"fieldInfoList\": [\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.Point\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"经度\",\n" +
                "            \"fieldName\": \"lat\",\n" +
                "            \"type\": \"Double\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.Point\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"纬度\",\n" +
                "            \"fieldName\": \"lng\",\n" +
                "            \"type\": \"Double\"\n" +
                "          }\n" +
                "        ],\n" +
                "        \"root\": false\n" +
                "      },\n" +
                "      {\n" +
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
                "      },\n" +
                "      {\n" +
                "        \"clsName\": \"ReceiveAddress\",\n" +
                "        \"description\": \"收货地址\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          \n" +
                "        ],\n" +
                "        \"fieldInfoList\": [\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ReceiveAddress\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"详细地址\",\n" +
                "            \"fieldName\": \"address\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ReceiveAddress\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"收货人姓名\",\n" +
                "            \"fieldName\": \"name\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ReceiveAddress\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"收货人电话\",\n" +
                "            \"fieldName\": \"phone\",\n" +
                "            \"type\": \"String\"\n" +
                "          },\n" +
                "          {\n" +
                "            \"clsType\": \"cn.easylib.ddd.sample.domain.order.entity.valueobject.ReceiveAddress\",\n" +
                "            \"collection\": false,\n" +
                "            \"description\": \"坐标\",\n" +
                "            \"fieldName\": \"point\",\n" +
                "            \"type\": \"Point\"\n" +
                "          }\n" +
                "        ],\n" +
                "        \"root\": false\n" +
                "      }\n" +
                "    ]";

        return JSON.parseArray(jsonString, EntityDescriptor.class);
    }
}
