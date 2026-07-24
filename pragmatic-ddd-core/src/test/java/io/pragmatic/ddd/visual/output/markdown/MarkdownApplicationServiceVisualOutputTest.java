package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.application.ApplicationDescriptor;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;

/**
 *
 */
public class MarkdownApplicationServiceVisualOutputTest {

    @Test
    public void outputTest(){
        MarkdownApplicationServiceVisualOutput output = new MarkdownApplicationServiceVisualOutput();

        List<ApplicationDescriptor> applicationDescriptors = this.mockData();

        String output1 = output.output(applicationDescriptors);

        System.out.println(output1);
    }

    private List<ApplicationDescriptor> mockData(){

        String jsonString ="[\n" +
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
                "      }\n" +
                "    ]";

        return JSON.parseArray(jsonString,ApplicationDescriptor.class);
    }
}
