package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.DomainModelVisualInfo;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

public class MarkdownFullViewVisualOutputTest {

    @Test
    public void outputTest() {
        MarkdownFullViewVisualOutput markdownFullViewVisualOutput = new MarkdownFullViewVisualOutput();


        DomainModelVisualInfo domainModelVisualInfo = this.mockData();

        String output = markdownFullViewVisualOutput.output(domainModelVisualInfo);

        System.out.println(output);
    }

    private DomainModelVisualInfo mockData() {

        String jsonData = "{\n" +
                "    \"applicationDescriptors\": [\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"提交提交订单服务\",\n" +
                "        \"clsName\": \"OrderSubmitCommandService\",\n" +
                "        \"methodName\": \"submitOrder\",\n" +
                "        \"name\": \"订单提交\",\n" +
                "        \"type\": \"Command\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"订单取消\",\n" +
                "        \"clsName\": \"OrderCancelCommandService\",\n" +
                "        \"methodName\": \"cancel\",\n" +
                "        \"name\": \"订单取消\",\n" +
                "        \"type\": \"Command\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"支付完成后，修改订单的支付状态\",\n" +
                "        \"clsName\": \"OrderPayResultCommandService\",\n" +
                "        \"methodName\": \"payResult\",\n" +
                "        \"name\": \"修改订单支付结果\",\n" +
                "        \"type\": \"Command\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"根据订单ID查询订单信息\",\n" +
                "        \"clsName\": \"OrderReadService\",\n" +
                "        \"methodName\": \"queryById\",\n" +
                "        \"name\": \"\",\n" +
                "        \"type\": \"Query\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"根据条件查询一个订单\",\n" +
                "        \"clsName\": \"OrderReadService\",\n" +
                "        \"methodName\": \"oneQueryBy\",\n" +
                "        \"name\": \"\",\n" +
                "        \"type\": \"Query\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"根据条件查询一个订单信息\",\n" +
                "        \"clsName\": \"OrderReadService\",\n" +
                "        \"methodName\": \"listQueryBy\",\n" +
                "        \"name\": \"\",\n" +
                "        \"type\": \"Query\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"根据多个订单信息查询订单列表\",\n" +
                "        \"clsName\": \"OrderReadService\",\n" +
                "        \"methodName\": \"queryByIdList\",\n" +
                "        \"name\": \"\",\n" +
                "        \"type\": \"Query\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"分页查询订单列表\",\n" +
                "        \"clsName\": \"OrderReadService\",\n" +
                "        \"methodName\": \"pageQuery\",\n" +
                "        \"name\": \"\",\n" +
                "        \"type\": \"Query\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"applicationServiceDescription\": \"滚动查询订单列表\",\n" +
                "        \"clsName\": \"OrderReadService\",\n" +
                "        \"methodName\": \"scrollQuery\",\n" +
                "        \"name\": \"\",\n" +
                "        \"type\": \"Query\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"domainServiceDescriptors\": [\n" +
                "      {\n" +
                "        \"clsName\": \"IOrderIdGenerateService\",\n" +
                "        \"domainServiceDescription\": \"订单ID生成领域服务\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"entityDescriptorList\": [\n" +
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
                "        \"clsName\": \"Order\",\n" +
                "        \"description\": \"销售订单\",\n" +
                "        \"entityActionDescriptorList\": [\n" +
                "          {\n" +
                "            \"description\": \"构造\",\n" +
                "            \"methodName\": \"Order\",\n" +
                "            \"triggerEvents\": [\n" +
                "              \"OrderSubmittedEvent\"\n" +
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
                "            \"methodName\": \"payResult\",\n" +
                "            \"triggerEvents\": [\n" +
                "              \"OrderPayedEvent\"\n" +
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
                "    ],\n" +
                "    \"eventDescriptors\": [\n" +
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
                "    ],\n" +
                "    \"ruleDescriptorList\": [\n" +
                "      {\n" +
                "        \"name\": \"订单规则\",\n" +
                "        \"ruleDescriptorList\": [\n" +
                "          {\n" +
                "            \"ruleDescription\": \"订单ID>0\",\n" +
                "            \"ruleKey\": \"ORDER_ID_ERROR\",\n" +
                "            \"withConditionRule\": false\n" +
                "          },\n" +
                "          {\n" +
                "            \"ruleDescription\": \"订单支付超时时间错误\",\n" +
                "            \"ruleKey\": \"ORDER_PAY_TIMEOUT_ERROR\",\n" +
                "            \"withConditionRule\": true\n" +
                "          },\n" +
                "          {\n" +
                "            \"ruleDescription\": \"订单超过当日最大限制\",\n" +
                "            \"ruleKey\": \"ORDER_COUNT_OVER\",\n" +
                "            \"withConditionRule\": true\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    ]\n" +
                "  }";

        return JSON.parseObject(jsonData, DomainModelVisualInfo.class);
    }
}
