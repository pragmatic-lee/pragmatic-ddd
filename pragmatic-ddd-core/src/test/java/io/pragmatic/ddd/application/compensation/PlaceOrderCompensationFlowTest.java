package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;
import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.repository.IRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 下单编排的补偿全流程演示：以聚合驱动方式，把 CompensableCommandExecutor 作为"应用服务编排多个外部副作用"的载体。
 * 覆盖四种典型路径：全程成功不补偿、中途失败逆序补偿并重抛、补偿失败抛 CompensationFailedException、TCC confirm。
 *
 * @author wizard-lee
 */
class PlaceOrderCompensationFlowTest {

    /** 补偿重试无退避，便于演示补偿失败立即终止。 */
    private static final CompensationPolicy NO_RETRY = new CompensationPolicy(1, Duration.ZERO);

    @Test
    void placeOrder_allSucceed_noCompensation() {
        List<String> journal = new ArrayList<>();
        CompensableCommandExecutor executor = executor(
                new ReserveInventoryAction(journal),
                new DeductPointsAction(journal),
                new ChargePaymentAction(journal));

        // 聚合声明三个外部副作用，全部成功 → commit，不补偿
        executor.execute(new TestAggregate(), null, null, a -> {
            a.declare(new ReserveRequirement("SKU-001", 2));
            a.declare(new DeductRequirement("U-100", 50));
            a.declare(new ChargeRequirement("U-100", 100));
        });

        assertThat(journal).containsExactly(
                "reserve:SKU-001:2",
                "deduct:U-100:50",
                "charge:U-100:100");
    }

    @Test
    void placeOrder_paymentFails_reverseCompensateAndRethrowOriginal() {
        List<String> journal = new ArrayList<>();
        IllegalStateException paymentError = new IllegalStateException("支付网关超时");
        CompensableCommandExecutor executor = executor(
                new ReserveInventoryAction(journal),
                new DeductPointsAction(journal),
                new ChargePaymentAction(journal).failOnExecute(paymentError));

        // 收款（第三个动作）失败 → 已执行的库存/积分按逆序补偿，原异常被重抛
        assertThatThrownBy(() -> executor.execute(new TestAggregate(), null, null, a -> {
            a.declare(new ReserveRequirement("SKU-001", 2));
            a.declare(new DeductRequirement("U-100", 50));
            a.declare(new ChargeRequirement("U-100", 100));
        })).isSameAs(paymentError);

        // 所见即所偿：收款未登记成功，只补偿其前的积分、库存（逆序）；收款本身无补偿
        assertThat(journal).containsExactly(
                "reserve:SKU-001:2",
                "deduct:U-100:50",
                "restore:U-100",
                "release:SKU-001");
    }

    @Test
    void placeOrder_compensationFails_throwsCompensationFailedException() {
        List<String> journal = new ArrayList<>();
        IllegalStateException original = new IllegalStateException("积分服务暂不可用");
        CompensableCommandExecutor executor = executor(
                new ReserveInventoryAction(journal).withPolicy(NO_RETRY).failOnCompensate(),
                new DeductPointsAction(journal).failOnExecute(original));

        // 正向积分失败 → 已执行的库存补偿也失败 → 抛出 CompensationFailedException（携带原异常为 cause）
        assertThatThrownBy(() -> executor.execute(new TestAggregate(), null, null, a -> {
            a.declare(new ReserveRequirement("SKU-001", 2));
            a.declare(new DeductRequirement("U-100", 50));
        }))
                .isInstanceOf(CompensationFailedException.class)
                .hasCause(original)
                .satisfies(ex -> assertThat(((CompensationFailedException) ex).getFailures())
                        .extracting(CompensationFailure::actionKey)
                        .containsExactly("TestAggregate:1:reserve-inventory"));

        // 积分正向失败未登记，仅补偿已成功的库存（其补偿本身失败）
        assertThat(journal).containsExactly(
                "reserve:SKU-001:2",
                "release:SKU-001");
    }

    @Test
    void placeOrder_withConfirmableAction_confirmFiresOnSuccess() {
        List<String> journal = new ArrayList<>();
        CompensableCommandExecutor executor = executor(
                new ReserveInventoryAction(journal),
                new NotifyOrderCreatedAction(journal));

        // 实现 IConfirmableAction 的通知动作：提交成功后才收到 confirm（TCC Confirm 阶段）
        executor.execute(new TestAggregate(), null, null, a -> {
            a.declare(new ReserveRequirement("SKU-001", 2));
            a.declare(new NotifyRequirement("U-100"));
        });

        assertThat(journal).containsExactly(
                "reserve:SKU-001:2",
                "notify:U-100",
                "confirm:U-100");
    }

    private static CompensableCommandExecutor executor(ICompensableAction<?, ?, ?>... actions) {
        return new CompensableCommandExecutor(
                new NoOpDelegate(),
                null,
                CompensationOptions.inMemory(),
                new CompensationActionRegistry(List.of(actions)));
    }

    /** 落库 + 事件分发的空实现：本流程测试只关注外部动作与补偿编排。 */
    private static final class NoOpDelegate implements ICommandExecutor {

        @Override
        public <ID, T extends AggregateRoot<ID>> T execute(T aggregateRoot,
                                                           IRule<?> rule,
                                                           IRepository<ID, T> repository,
                                                           Consumer<T> domainLogic) {
            domainLogic.accept(aggregateRoot);
            return aggregateRoot;
        }
    }

    /** 库存预占需求：参数不在聚合上，由需求 record 承载。 */
    private record ReserveRequirement(String sku, int qty) implements IExternalRequirement {

        @Override
        public String code() {
            return "reserve-inventory";
        }
    }

    /** 扣减积分需求。 */
    private record DeductRequirement(String user, int points) implements IExternalRequirement {

        @Override
        public String code() {
            return "deduct-points";
        }
    }

    /** 支付扣款需求。 */
    private record ChargeRequirement(String user, int amount) implements IExternalRequirement {

        @Override
        public String code() {
            return "charge-payment";
        }
    }

    /** 下单通知需求。 */
    private record NotifyRequirement(String user) implements IExternalRequirement {

        @Override
        public String code() {
            return "notify-order";
        }
    }

    /** 预占库存：正向记录占用并回传 SKU，逆向按 SKU 释放。 */
    private static class ReserveInventoryAction
            implements ICompensableAction<TestAggregate, ReserveRequirement, String> {

        private final List<String> journal;
        private boolean failOnCompensate;
        private CompensationPolicy policy = CompensationPolicy.defaultPolicy();

        ReserveInventoryAction(List<String> journal) {
            this.journal = journal;
        }

        ReserveInventoryAction failOnCompensate() {
            this.failOnCompensate = true;
            return this;
        }

        ReserveInventoryAction withPolicy(CompensationPolicy policy) {
            this.policy = policy;
            return this;
        }

        @Override
        public Class<ReserveRequirement> requirementType() {
            return ReserveRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, ReserveRequirement requirement) {
            journal.add("reserve:" + requirement.sku() + ":" + requirement.qty());
            return requirement.sku();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String sku) {
            journal.add("release:" + sku);
            if (failOnCompensate) {
                throw new IllegalStateException("释放库存失败: " + sku);
            }
        }

        @Override
        public String payload(TestAggregate aggregateRoot, ReserveRequirement requirement) {
            return requirement.sku() + ":" + requirement.qty();
        }

        @Override
        public Optional<CompensationPolicy> policy() {
            return Optional.of(policy);
        }
    }

    /** 扣减积分：正向扣减并回传用户，逆向回退；可配置正向失败。 */
    private static class DeductPointsAction
            implements ICompensableAction<TestAggregate, DeductRequirement, String> {

        private final List<String> journal;
        private RuntimeException executeError;

        DeductPointsAction(List<String> journal) {
            this.journal = journal;
        }

        DeductPointsAction failOnExecute(RuntimeException error) {
            this.executeError = error;
            return this;
        }

        @Override
        public Class<DeductRequirement> requirementType() {
            return DeductRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, DeductRequirement requirement) {
            if (executeError != null) {
                throw executeError;
            }
            journal.add("deduct:" + requirement.user() + ":" + requirement.points());
            return requirement.user();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String user) {
            journal.add("restore:" + user);
        }

        @Override
        public String payload(TestAggregate aggregateRoot, DeductRequirement requirement) {
            return requirement.user() + ":" + requirement.points();
        }
    }

    /** 收款：正向扣款并回传用户，逆向退款；可配置正向失败。 */
    private static class ChargePaymentAction
            implements ICompensableAction<TestAggregate, ChargeRequirement, String> {

        private final List<String> journal;
        private RuntimeException executeError;

        ChargePaymentAction(List<String> journal) {
            this.journal = journal;
        }

        ChargePaymentAction failOnExecute(RuntimeException error) {
            this.executeError = error;
            return this;
        }

        @Override
        public Class<ChargeRequirement> requirementType() {
            return ChargeRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, ChargeRequirement requirement) {
            if (executeError != null) {
                throw executeError;
            }
            journal.add("charge:" + requirement.user() + ":" + requirement.amount());
            return requirement.user();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String user) {
            journal.add("refund:" + user);
        }

        @Override
        public String payload(TestAggregate aggregateRoot, ChargeRequirement requirement) {
            return requirement.user() + ":" + requirement.amount();
        }
    }

    /** 下单通知：实现 IConfirmableAction，提交成功后收到 confirm（TCC Confirm）。 */
    private static class NotifyOrderCreatedAction
            implements IConfirmableAction<TestAggregate, NotifyRequirement, String> {

        private final List<String> journal;

        NotifyOrderCreatedAction(List<String> journal) {
            this.journal = journal;
        }

        @Override
        public Class<NotifyRequirement> requirementType() {
            return NotifyRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, NotifyRequirement requirement) {
            journal.add("notify:" + requirement.user());
            return requirement.user();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String user) {
            journal.add("cancel-notify:" + user);
        }

        @Override
        public String payload(TestAggregate aggregateRoot, NotifyRequirement requirement) {
            return requirement.user();
        }

        @Override
        public void confirm(TestAggregate aggregateRoot, String user) {
            journal.add("confirm:" + user);
        }
    }
}
