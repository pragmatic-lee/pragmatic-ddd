package io.pragmatic.ddd.example.order.infrastructure.order.dependency;

import io.pragmatic.ddd.acl.AbstractIdempotentWriteGateway;
import io.pragmatic.ddd.acl.ExternalCallLogger;
import io.pragmatic.ddd.example.order.domain.order.dependency.IUserPointsDependency;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.IncreasePointsCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 积分服务防腐适配器：按 bizId 先查后写，保证重复事件不重复发放（打桩实现）。
 *
 * @author wizard-lee
 */
@Component
public class UserPointsDependencyAdapter
        extends AbstractIdempotentWriteGateway<IncreasePointsCommand, Void, PointsRequest, PointsResponse, String>
        implements IUserPointsDependency {

    private static final Logger log = LoggerFactory.getLogger(UserPointsDependencyAdapter.class);

    private final Map<String, PointsResponse> existingRecords = new ConcurrentHashMap<>();

    public UserPointsDependencyAdapter() {
        setLogger(new ExternalCallLogger<>() {
            @Override
            public void onRequest(PointsRequest request) {
                log.info("积分发放请求: customerId={}, points={}, bizId={}",
                        request.customerId(), request.points(), request.bizId());
            }

            @Override
            public void onResponse(PointsResponse response) {
                log.info("积分发放响应: bizId={}, success={}", response.bizId(), response.success());
            }

            @Override
            public void onError(Throwable ex) {
                log.error("积分发放异常", ex);
            }
        });
    }

    @Override
    public void increasePoints(IncreasePointsCommand command) {
        log.info("收到积分发放指令: customerId={}, points={}, bizId={}",
                command.customerId(), command.points(), command.bizId());
        write(command);
    }

    @Override
    protected String uniqueKey(IncreasePointsCommand param) {
        return param.bizId();
    }

    @Override
    protected Optional<PointsResponse> queryByKey(String key) {
        // 打桩：真实场景查询积分流水表，判断该 bizId 是否已发放
        if (existingRecords.containsKey(key)) {
            log.info("积分幂等命中，跳过重复发放: bizId={}", key);
            return Optional.of(existingRecords.get(key));
        }
        return Optional.empty();
    }

    @Override
    protected Void toDomainResultFromExisting(PointsResponse existing) {
        return null; // 已发放过，短路返回
    }

    @Override
    protected PointsRequest toExternalRequest(IncreasePointsCommand param) {
        return new PointsRequest(param.customerId(), param.points(), param.bizId());
    }

    @Override
    protected PointsResponse doWrite(PointsRequest request) {
        // 打桩：真实场景调用积分服务
        PointsResponse response = new PointsResponse(request.bizId(), true);
        existingRecords.put(request.bizId(), response);
        return response;
    }

    @Override
    protected Void toDomainResult(PointsResponse response) {
        return null;
    }
}
