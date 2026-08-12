package io.pragmatic.ddd.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.pragmatic.ddd.afull.domain.order.event.OrderCreatedEvent;
import io.pragmatic.ddd.afull.domain.order.service.IOrderCreatedNoticeWarehouseHandler;
import io.pragmatic.ddd.afull.domain.order.service.IOrderIdGenerator;
import io.pragmatic.ddd.afull.domain.order.service.IUserValidityRule;
import org.junit.jupiter.api.Test;

import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.event.spi.IHandle;

/**
 * 验证：@DomainService 注解元数据可被反射读取，且 IDomainService.category() 按注解返回分类。
 *
 * @author wizard-lee
 */
class DomainServiceCategoryTest {

    @DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
            targetName = "OrderCreatedEvent", description = "示例事件订阅")
    public interface SampleEventSubscriber extends IEventSubscriberService<OrderCreatedEvent> {
    }

    @DomainService(category = DomainServiceCategory.RULE_VALIDATOR, targetName = "Sample")
    public interface SampleRuleValidator extends IRuleValidatorService {
    }

    @DomainService(category = DomainServiceCategory.ATTRIBUTE_CALCULATOR, targetName = "Sample")
    public interface SampleAttributeCalculator extends IAttributeCalculatorService {
    }

    @DomainService(category = DomainServiceCategory.CAPABILITY_PROVIDER, targetName = "SampleId")
    public interface SampleCapabilityProvider extends ICapabilityProviderService {
    }

    public interface UnannotatedService extends IDomainService {
    }

    @Test
    void shouldReadAnnotationMetadata() {
        DomainService meta = SampleEventSubscriber.class.getAnnotation(DomainService.class);
        assertThat(meta).isNotNull();
        assertThat(meta.category()).isEqualTo(DomainServiceCategory.EVENT_SUBSCRIBER);
        assertThat(meta.targetName()).isEqualTo("OrderCreatedEvent");
        assertThat(meta.description()).isEqualTo("示例事件订阅");
    }

    @Test
    void shouldReturnUnknownWhenNotAnnotated() {
        assertThat(UnannotatedService.class.getAnnotation(DomainService.class)).isNull();
        assertThat(new UnannotatedService() {
        }.category()).isEqualTo(DomainServiceCategory.UNKNOWN);
    }

    @Test
    void shouldReturnCategoryByAnnotation() {
        assertThat(new SampleEventSubscriber() {
            @Override
            public void handleEvent(OrderCreatedEvent event) {
            }
        }.category()).isEqualTo(DomainServiceCategory.EVENT_SUBSCRIBER);
        assertThat(new SampleRuleValidator() {
        }.category()).isEqualTo(DomainServiceCategory.RULE_VALIDATOR);
        assertThat(new SampleAttributeCalculator() {
        }.category()).isEqualTo(DomainServiceCategory.ATTRIBUTE_CALCULATOR);
        assertThat(new SampleCapabilityProvider() {
        }.category()).isEqualTo(DomainServiceCategory.CAPABILITY_PROVIDER);
    }

    @Test
    void baseInterfacesShouldExtendDomainServiceAndHandle() {
        assertThat(IDomainService.class.isAssignableFrom(IEventSubscriberService.class)).isTrue();
        assertThat(IHandle.class.isAssignableFrom(IEventSubscriberService.class)).isTrue();
        assertThat(IDomainService.class.isAssignableFrom(IRuleValidatorService.class)).isTrue();
        assertThat(IDomainService.class.isAssignableFrom(IAttributeCalculatorService.class)).isTrue();
        assertThat(IDomainService.class.isAssignableFrom(ICapabilityProviderService.class)).isTrue();
    }

    @Test
    void afullContractsShouldResolveCategory() {
        assertThat(new IOrderIdGenerator() {
            @Override
            public long generate() {
                return 0L;
            }
        }.category()).isEqualTo(DomainServiceCategory.CAPABILITY_PROVIDER);

        assertThat(new IUserValidityRule() {
            @Override
            public RuleCheckResult check(String pin) {
                return RuleCheckResult.pass();
            }
        }.category()).isEqualTo(DomainServiceCategory.RULE_VALIDATOR);

        assertThat(new IOrderCreatedNoticeWarehouseHandler() {
            @Override
            public void handleEvent(OrderCreatedEvent event) {
            }
        }.category()).isEqualTo(DomainServiceCategory.EVENT_SUBSCRIBER);
    }
}
