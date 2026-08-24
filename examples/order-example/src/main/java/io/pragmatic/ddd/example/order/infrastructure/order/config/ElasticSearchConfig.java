package io.pragmatic.ddd.example.order.infrastructure.order.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 订单示例的 Elasticsearch 客户端配置。
 * 基于 ES 8.17 官方 elasticsearch-java 客户端，构造 RestClient -> ElasticsearchTransport -> ElasticsearchClient 三层对象，
 * 不依赖 Spring Data Elasticsearch 的自动装配（本项目所有 Bean 均手写提供）。
 *
 * <p>连接地址、认证信息、超时参数均从外部化配置读取（application.properties 或环境变量），不在代码中硬编码。</p>
 *
 * @author wizard-lee
 */
@Configuration
public class ElasticSearchConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3_000;
    private static final int DEFAULT_SOCKET_TIMEOUT_MILLIS = 30_000;

    @Value("${elasticsearch.hosts:http://localhost:9200}")
    private String hosts;

    @Value("${elasticsearch.username:}")
    private String username;

    @Value("${elasticsearch.password:}")
    private String password;

    @Value("${elasticsearch.connect-timeout:" + DEFAULT_CONNECT_TIMEOUT_MILLIS + "}")
    private int connectTimeout;

    @Value("${elasticsearch.socket-timeout:" + DEFAULT_SOCKET_TIMEOUT_MILLIS + "}")
    private int socketTimeout;

    /**
     * 构建低层 RestClient（基于 Apache HttpClient），负责与 ES 集群的实际 HTTP 通信。
     * 支持多节点逗号分隔配置，并可选注入用户名/密码 Basic 认证。
     *
     * @return RestClient 实例
     */
    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient() {
        List<HttpHost> httpHosts = parseHosts();
        RestClientBuilder builder = RestClient.builder(httpHosts.toArray(new HttpHost[0]));

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
               .setConnectTimeout(connectTimeout)
               .setSocketTimeout(socketTimeout));

        credentialsProvider().ifPresent(builder::setHttpClientConfigCallback);

        return builder.build();
    }

    /**
     * 构建 ElasticsearchTransport，封装 JSON 序列化（Jackson）与 RestClient 的传输契约。
     *
     * @param restClient 低层 RestClient
     * @return ElasticsearchTransport 实例
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    /**
     * 构建高层类型安全的 ElasticsearchClient，供仓储与查询服务注入使用。
     *
     * @param transport ElasticsearchTransport
     * @return ElasticsearchClient 实例
     */
    @Bean(destroyMethod = "close")
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }

    private Optional<RestClientBuilder.HttpClientConfigCallback> credentialsProvider() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return java.util.Optional.empty();
        }
        BasicCredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
        return java.util.Optional.of(httpClientBuilder ->
                httpClientBuilder.setDefaultCredentialsProvider(provider));
    }

    private List<HttpHost> parseHosts() {
        return Arrays.stream(hosts.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(HttpHost::create)
                .toList();
    }
}
