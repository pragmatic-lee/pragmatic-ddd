package io.pragmatic.ddd.visual.output.markdown;

import io.pragmatic.ddd.visual.rule.RuleDescriptorGroup;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;

public class MarkdownEntityRuleVisualOutputTest {

    @Test
    public void outputTest() {
        MarkdownEntityRuleVisualOutput markDownEntityRuleVisualOutput = new MarkdownEntityRuleVisualOutput();

        List<RuleDescriptorGroup> ruleDescriptorGroup = this.mockData();
        String output = markDownEntityRuleVisualOutput.output(ruleDescriptorGroup);

        System.out.println(output);
    }

    private List<RuleDescriptorGroup> mockData() {
        String jsonString = "[\n" +
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
                "    ]";

        return JSON.parseArray(jsonString, RuleDescriptorGroup.class);
    }
}
