package io.pragmatic.ddd.example.order.controller;

import io.pragmatic.ddd.example.order.application.order.OrderWriteService;
import io.pragmatic.ddd.example.order.application.order.input.ChangeOrderAddressInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderAddressInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderItemInput;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;



/**
 * 示例首版启动测试控制器，用于验证应用可正常启动并响应请求。
 *
 * @author wizard-lee
 */
@RestController
public class IndexController {

    @Resource
    private OrderWriteService orderWriteService;

    @GetMapping("/")
    public String index() {
        return "Pragmatic DDD Order Example is running.";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }

    @GetMapping("/testOrder")
    public String testOrder(){
        CreateOrderInput input = buildSampleInput();
        orderWriteService.placeOrder(input);
        return "OK";
    }

    @GetMapping("/testChangeAddress")
    public String testChangeAddress(@RequestParam Long orderId){
        ChangeOrderAddressInput input = new ChangeOrderAddressInput();
        input.setProvince("北京市");
        input.setCity("北京市");
        input.setDistrict("海淀区");
        input.setDetail("中关村大街 5 号院腾讯大厦");
        input.setReceiverName("李四");
        input.setReceiverPhone("13900139000");

        orderWriteService.changeOrderAddress(orderId, input);
        return "OK";
    }

    private CreateOrderInput buildSampleInput(){
        CreateOrderInput input = new CreateOrderInput();
        input.setCustomerId(1001L);
        input.setCustomerName("张三");
        input.setRemark("测试下单");
        input.setPaymentMethod("WECHAT");

        CreateOrderAddressInput address = new CreateOrderAddressInput();
        address.setProvince("广东省");
        address.setCity("深圳市");
        address.setDistrict("南山区");
        address.setDetail("科技园路 1 号");
        address.setReceiverName("张三");
        address.setReceiverPhone("13800138000");
        input.setShippingAddress(address);

        CreateOrderItemInput item = new CreateOrderItemInput();
        item.setProductId(2001L);
        item.setProductName("机械键盘");
        item.setSpec("87 键茶轴");
        item.setUnitPriceAmount(new BigDecimal("299.00"));
        item.setUnitPriceCurrency("CNY");
        item.setQuantity(2);
        input.setOrderItems(List.of(item));

        return input;
    }
}
