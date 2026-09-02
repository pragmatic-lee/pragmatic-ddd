package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderByIdSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderEsProjector;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderListSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderOneSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderPageSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer.OrderEsSource;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 订单 ES 物化器集成测试，针对本地已就绪的 Elasticsearch（索引 order_index）验证物化与清理行为。
 * 不 Mock 客户端，直接连接 http://localhost:9200，确保与真实 ES 写入、乐观并发、删除语义一致。
 *
 * <p>说明：物化写入采用 ES external 版本（单调递增），故测试版本号取时间种子而非固定小写，
 * 以避免与本地索引残留的 external 计数器冲突。</p>
 *
 * @author wizard-lee
 */
@DisplayName("OrderEsSource 集成测试")
class OrderEsMaterializerTest {

    private static final String INDEX_NAME = "order_index";

    private static final Long TEST_ORDER_ID = 990001L;

    private ElasticsearchClient elasticsearchClient;

    private RestClient restClient;

    private OrderEsSource materializer;

    private long versionSeed = System.currentTimeMillis();

    @BeforeEach
    void setUp() {
        restClient = RestClient.builder(HttpHost.create("http://localhost:9200")).build();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
        elasticsearchClient = new ElasticsearchClient(transport);
        materializer = new OrderEsSource(
                new OrderEsProjector(),
                new OrderByIdSearcher(elasticsearchClient),
                new OrderOneSearcher(elasticsearchClient),
                new OrderListSearcher(elasticsearchClient),
                new OrderPageSearcher(elasticsearchClient),
                new OrderSummaryReducer(),
                elasticsearchClient);
        deleteTestDocumentSilently();
    }

    @AfterEach
    void tearDown() {
        deleteTestDocumentSilently();
        try {
            restClient.close();
        } catch (IOException ignored) {
            // 关闭客户端异常忽略
        }
    }

    /** 单调递增的版本号，恒大于本地 external 版本计数器。 */
    private long nextVersion() {
        return versionSeed++;
    }

    private void deleteTestDocumentSilently() {
        try {
            elasticsearchClient.delete(req -> req.index(INDEX_NAME).id(TEST_ORDER_ID.toString()));
        } catch (Exception ignored) {
            // 文档可能不存在，清理时忽略删除异常
        }
    }

    private OrderEsProjection sampleProjection() {
        OrderEsProjection projection = new OrderEsProjection();
        projection.setOrderId(TEST_ORDER_ID);
        projection.setStatus(1);
        projection.setStatusName("待支付");
        projection.setTotalAmount(10000L);
        projection.setActualAmount(9800L);
        projection.setPlatformDiscount(200L);
        projection.setCurrency("CNY");
        projection.setCreatedAt(LocalDateTime.now());
        projection.setUpdatedAt(LocalDateTime.now());
        return projection;
    }

    private OrderEsProjection sampleFullProjection() {
        OrderEsProjection projection = new OrderEsProjection();
        projection.setOrderId(TEST_ORDER_ID);
        projection.setStatus(2);
        projection.setStatusName("已发货");
        projection.setTotalAmount(29900L);
        projection.setActualAmount(29900L);
        projection.setPlatformDiscount(0L);
        projection.setCurrency("CNY");
        projection.setPaymentMethod(1);
        projection.setPaymentMethodName("微信支付");
        projection.setPaymentSerialNo("WX2026082000123");
        projection.setPaidAt(LocalDateTime.now().minusHours(2));
        projection.setRemark("请尽快发货");
        projection.setCreatedAt(LocalDateTime.now().minusDays(1));
        projection.setUpdatedAt(LocalDateTime.now());

        OrderEsProjection.CustomerProjection customer = new OrderEsProjection.CustomerProjection();
        customer.setCustomerId(10086L);
        customer.setCustomerName("张三");
        projection.setCustomer(customer);

        OrderEsProjection.AddressProjection address = new OrderEsProjection.AddressProjection();
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("西湖区");
        address.setDetail("文三路 100 号");
        address.setReceiverName("张三");
        address.setReceiverPhone("13800138000");
        projection.setShippingAddress(address);

        OrderEsProjection.LogisticsProjection logistics = new OrderEsProjection.LogisticsProjection();
        logistics.setTrackingNo("SF1234567890");
        logistics.setCompanyCode("SF");
        logistics.setCompanyName("顺丰速运");
        logistics.setShippedAt(LocalDateTime.now().minusHours(1));
        projection.setLogisticsInfo(logistics);

        OrderEsProjection.OrderItemProjection item1 = new OrderEsProjection.OrderItemProjection();
        item1.setItemId(1L);
        item1.setProductId(2001L);
        item1.setProductName("机械键盘");
        item1.setSpec("青轴");
        item1.setPrice(19900L);
        item1.setQuantity(1);
        item1.setSubtotal(19900L);

        OrderEsProjection.OrderItemProjection item2 = new OrderEsProjection.OrderItemProjection();
        item2.setItemId(2L);
        item2.setProductId(2002L);
        item2.setProductName("鼠标垫");
        item2.setSpec("加厚");
        item2.setPrice(10000L);
        item2.setQuantity(1);
        item2.setSubtotal(10000L);

        projection.setOrderItems(java.util.List.of(item1, item2));
        projection.setItemProductNames(java.util.List.of("机械键盘", "鼠标垫"));
        projection.setItemProductNamesText("机械键盘 鼠标垫");

        return projection;
    }

    @Test
    @DisplayName("projectionType 返回订单 ES 投影类型")
    void projectionTypeReturnsOrderEsProjection() {
        assertThat(materializer.projectionType()).isEqualTo(OrderEsProjection.class);
    }

    @Test
    @DisplayName("source 返回订单 ES 源标识 es:orders")
    void sourceReturnsOrderEsIdentifier() {
        assertThat(materializer.source().id()).isEqualTo(OrderEsTargets.TARGET_ES_ORDERS.storeId());
        assertThat(materializer.projectionType()).isEqualTo(OrderEsProjection.class);
    }

    @Test
    @DisplayName("materialize 写入投影并以其版本作为文档 _version")
    void materializeWritesProjectionWithVersion() throws IOException {
        OrderEsProjection projection = sampleProjection();
        long version = nextVersion();

        materializer.materialize(projection, version);

        GetResponse<OrderEsProjection> response =
                elasticsearchClient.get(req -> req.index(INDEX_NAME).id(TEST_ORDER_ID.toString()),
                        OrderEsProjection.class);
        assertThat(response.found()).isTrue();
        assertThat(response.source()).isNotNull();
        assertThat(response.source().getOrderId()).isEqualTo(TEST_ORDER_ID);
        assertThat(response.source().getStatusName()).isEqualTo("待支付");
        assertThat(response.version()).isEqualTo(version);
    }

    @Test
    @DisplayName("materialize 高版本覆盖低版本成功，并以高版本作为 _version")
    void materializeHigherVersionOverwrites() throws IOException {
        OrderEsProjection first = sampleProjection();
        long lower = nextVersion();
        materializer.materialize(first, lower);

        OrderEsProjection second = sampleProjection();
        second.setStatusName("已支付");
        long higher = nextVersion();
        materializer.materialize(second, higher);

        GetResponse<OrderEsProjection> response =
                elasticsearchClient.get(req -> req.index(INDEX_NAME).id(TEST_ORDER_ID.toString()),
                        OrderEsProjection.class);
        assertThat(response.found()).isTrue();
        assertThat(response.source().getStatusName()).isEqualTo("已支付");
        assertThat(response.version()).isEqualTo(higher);
    }

    @Test
    @DisplayName("materialize 相同或更低版本写入被 ES 乐观并发拒绝，文档版本保持不变（异常被物化器静默吞掉）")
    void materializeLowerOrEqualVersionIsRejectedSilently() throws IOException {
        OrderEsProjection base = sampleProjection();
        long version = nextVersion();
        materializer.materialize(base, version);

        OrderEsProjection stale = sampleProjection();
        stale.setStatusName("待支付");
        materializer.materialize(stale, version);

        GetResponse<OrderEsProjection> response =
                elasticsearchClient.get(req -> req.index(INDEX_NAME).id(TEST_ORDER_ID.toString()),
                        OrderEsProjection.class);
        assertThat(response.found()).isTrue();
        assertThat(response.version()).isEqualTo(version);
        assertThat(response.source().getStatusName()).isEqualTo("待支付");
    }

    @Test
    @DisplayName("materialize 写入含嵌套对象与明细的完整投影，回读字段一致")
    void materializeWritesFullProjection() throws IOException {
        OrderEsProjection projection = sampleFullProjection();
        long version = nextVersion();

        materializer.materialize(projection, version);

        GetResponse<OrderEsProjection> response =
                elasticsearchClient.get(req -> req.index(INDEX_NAME).id(TEST_ORDER_ID.toString()),
                        OrderEsProjection.class);
        OrderEsProjection source = response.source();
        assertThat(response.found()).isTrue();
        assertThat(source).isNotNull();
        assertThat(response.version()).isEqualTo(version);

        assertThat(source.getStatusName()).isEqualTo("已发货");
        assertThat(source.getPaymentMethodName()).isEqualTo("微信支付");
        assertThat(source.getPaymentSerialNo()).isEqualTo("WX2026082000123");

        assertThat(source.getCustomer()).isNotNull();
        assertThat(source.getCustomer().getCustomerId()).isEqualTo(10086L);
        assertThat(source.getCustomer().getCustomerName()).isEqualTo("张三");

        assertThat(source.getShippingAddress()).isNotNull();
        assertThat(source.getShippingAddress().getProvince()).isEqualTo("浙江省");
        assertThat(source.getShippingAddress().getCity()).isEqualTo("杭州市");
        assertThat(source.getShippingAddress().getReceiverPhone()).isEqualTo("13800138000");

        assertThat(source.getLogisticsInfo()).isNotNull();
        assertThat(source.getLogisticsInfo().getTrackingNo()).isEqualTo("SF1234567890");
        assertThat(source.getLogisticsInfo().getCompanyName()).isEqualTo("顺丰速运");

        assertThat(source.getOrderItems()).isNotNull().hasSize(2);
        assertThat(source.getOrderItems()).extracting("productName")
                .containsExactly("机械键盘", "鼠标垫");
        assertThat(source.getOrderItems()).extracting("subtotal")
                .containsExactly(19900L, 10000L);

        assertThat(source.getItemProductNames()).containsExactly("机械键盘", "鼠标垫");
        assertThat(source.getItemProductNamesText()).isEqualTo("机械键盘 鼠标垫");
    }

    @Test
    @DisplayName("purge 删除已存在的投影文档")
    void purgeRemovesExistingDocument() throws IOException {
        materializer.materialize(sampleProjection(), nextVersion());

        materializer.purge(TEST_ORDER_ID);

        GetResponse<OrderEsProjection> response =
                elasticsearchClient.get(req -> req.index(INDEX_NAME).id(TEST_ORDER_ID.toString()),
                        OrderEsProjection.class);
        assertThat(response.found()).isFalse();
    }

    @Test
    @DisplayName("purge 删除不存在的文档静默忽略，不抛异常")
    void purgeNonExistingDocumentSilentlyIgnored() {
        assertThatCode(() -> materializer.purge(TEST_ORDER_ID)).doesNotThrowAnyException();
    }
}
