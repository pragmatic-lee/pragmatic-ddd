package io.pragmatic.ddd.example.order.controller.order;

import io.pragmatic.ddd.example.order.api.common.ApiErrorCode;
import io.pragmatic.ddd.example.order.api.common.ApiException;
import io.pragmatic.ddd.example.order.api.common.MockLoginContext;
import io.pragmatic.ddd.example.order.api.common.PageResultDTO;
import io.pragmatic.ddd.example.order.api.common.Result;
import io.pragmatic.ddd.example.order.api.order.dto.OrderDetailDTO;
import io.pragmatic.ddd.example.order.api.order.dto.OrderSummaryDTO;
import io.pragmatic.ddd.example.order.api.order.dto.SubmitOrderResponseDTO;
import io.pragmatic.ddd.example.order.api.order.request.PayOrderRequest;
import io.pragmatic.ddd.example.order.api.order.request.SearchOrderRequest;
import io.pragmatic.ddd.example.order.api.order.request.SubmitOrderRequest;
import io.pragmatic.ddd.example.order.application.order.OrderReadService;
import io.pragmatic.ddd.example.order.application.order.OrderWriteService;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderAddressInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderInput;
import io.pragmatic.ddd.example.order.application.order.input.CreateOrderItemInput;
import io.pragmatic.ddd.example.order.application.order.input.PayOrderInput;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 订单 HTTP 接口（对齐 order-backend-api-design.md 4.1~4.4）。
 * 路径前缀 /api 由 application.properties 的 server.servlet.context-path 提供。
 *
 * @author wizard-lee
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderWriteService orderWriteService;

    private final OrderReadService orderReadService;

    private final MockLoginContext loginContext;

    public OrderController(OrderWriteService orderWriteService,
                           OrderReadService orderReadService,
                           MockLoginContext loginContext) {
        this.orderWriteService = orderWriteService;
        this.orderReadService = orderReadService;
        this.loginContext = loginContext;
    }

    /** 提交订单。 */
    @PostMapping
    public Result<SubmitOrderResponseDTO> submitOrder(@RequestBody SubmitOrderRequest request) {
        validateSubmitRequest(request);
        Order order = orderWriteService.placeOrder(toCreateOrderInput(request));
        return Result.ok(SubmitOrderResponseDTO.from(order));
    }

    /**
     * 订单分页查询。
     * 当前仅透传底层已支持的 orderId/status/customerId 条件；
     * trackingNo/remark/金额区间/时间区间等条件待查询能力扩展后接入（见设计文档 4.2）。
     */
    @GetMapping
    public Result<PageResultDTO<OrderSummaryDTO>> queryOrders(SearchOrderRequest request) {
        PageRequest pageRequest = PageRequest.of(request.getPageNo(), request.getPageSize());
        OrderPageQuery.ByConditions criteria = new OrderPageQuery.ByConditions(
                Optional.ofNullable(request.getOrderId()),
                Optional.ofNullable(request.getStatus()),
                Optional.empty(),
                Optional.empty(),
                Optional.ofNullable(request.getCustomerId()));
        PageResult<OrderEsProjection> page = orderReadService.queryPage(
                criteria,
                pageRequest,
                OrderEsProjection.class);
        List<OrderSummaryDTO> list = page.data().stream()
                .map(OrderSummaryDTO::from)
                .toList();
        return Result.ok(PageResultDTO.of(
                list,
                page.totalCount(),
                pageRequest.pageNumber(),
                pageRequest.pageSize()));
    }

    /** 订单详情（读模型 ES 全量投影裁剪）。 */
    @GetMapping("/{orderId}")
    public Result<OrderDetailDTO> orderDetail(@PathVariable Long orderId) {
        OrderEsProjection projection = orderReadService.queryById(orderId, OrderEsProjection.class);
        if (projection == null) {
            throw new ApiException(ApiErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return Result.ok(OrderDetailDTO.from(projection));
    }

    /**
     * 订单支付。
     * payMethod 仅接收，暂不做与订单记录的一致性校验（枚举字典统一后启用，见设计文档拍板 #4）。
     */
    @PostMapping("/{orderId}/payment")
    public Result<OrderDetailDTO> payOrder(@PathVariable Long orderId, @RequestBody PayOrderRequest request) {
        if (request.getOrderId() != null && !request.getOrderId().equals(orderId)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "路径与请求体中的 orderId 不一致");
        }
        Order order = orderWriteService.payOrder(orderId, toPayOrderInput(request));
        if (order == null) {
            throw new ApiException(ApiErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return Result.ok(OrderDetailDTO.from(order));
    }

    private void validateSubmitRequest(SubmitOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "下单商品项不能为空");
        }
        if (request.getAddress() == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "收货地址不能为空");
        }
    }

    private CreateOrderInput toCreateOrderInput(SubmitOrderRequest request) {
        CreateOrderInput input = new CreateOrderInput();
        input.setCustomerId(loginContext.getCustomerId());
        input.setCustomerName(loginContext.getCustomerName());
        // 支付方式后置于支付接口选择，下单阶段绑定默认支付方式（枚举字典统一后收口，见设计文档 3.4.2）
        input.setPaymentMethod(PaymentMethod.WECHAT.name());
        input.setRemark(null);
        input.setShippingAddress(toCreateOrderAddressInput(request.getAddress()));
        input.setOrderItems(request.getItems().stream()
                .map(this::toCreateOrderItemInput)
                .toList());
        return input;
    }

    private CreateOrderAddressInput toCreateOrderAddressInput(SubmitOrderRequest.Address address) {
        CreateOrderAddressInput input = new CreateOrderAddressInput();
        input.setProvince(address.getProvince());
        input.setCity(address.getCity());
        input.setDistrict(address.getDistrict());
        input.setDetail(address.getDetail());
        input.setReceiverName(address.getReceiverName());
        input.setReceiverPhone(address.getReceiverPhone());
        return input;
    }

    private CreateOrderItemInput toCreateOrderItemInput(SubmitOrderRequest.Item item) {
        CreateOrderItemInput input = new CreateOrderItemInput();
        input.setProductId(item.getProductId());
        input.setProductName(item.getProductName());
        input.setSpec(null);
        input.setUnitPriceAmount(item.getPrice());
        input.setUnitPriceCurrency("CNY");
        input.setQuantity(item.getQuantity());
        return input;
    }

    private PayOrderInput toPayOrderInput(PayOrderRequest request) {
        PayOrderInput input = new PayOrderInput();
        input.setPaymentSerialNo(request.getPayTransactionNo());
        input.setCurrency("CNY");
        input.setAmount(request.getActualAmount());
        if (request.getPlatformDiscountAmount() != null) {
            input.setPlatformDiscountAmount(request.getPlatformDiscountAmount());
        } else {
            input.setPlatformDiscountAmount(BigDecimal.ZERO);
        }
        return input;
    }
}
