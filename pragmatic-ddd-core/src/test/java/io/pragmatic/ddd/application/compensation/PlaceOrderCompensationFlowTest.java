package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.IExternalRequirement;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 下单编排的补偿全流程演示：把 CompensationTemplate 作为一个"应用服务编排多个外部副作用"的真实载体。
 * 覆盖四种典型路径：全程成功不补偿、中途失败逆序补偿并重抛、返回值的 call + TCC confirm、补偿失败抛 CompensationFailedException。
 * v3 形态下各动作的幂等键由框架按需求生成，业务参数改由需求 record 承载。
 *
 * @author wizard-lee
 */
class PlaceOrderCompensationFlowTest {

    /** 补偿重试无退避，便于演示补偿失败立即终止。 */
    private static final CompensationPolicy NO_RETRY = new CompensationPolicy(1, Duration.ZERO);

    @Test
    void placeOrder_allSucceed_noCompensation() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager();

        // 模板登记三个外部副作用，全部成功 → commit，不补偿
        CompensationTemplate.run(manager, scope -> {
            scope.execute(new ReserveInventoryAction(journal, "SKU-001", 2).command());
            scope.execute(new DeductPointsAction(journal, "U-100", 50).command());
            scope.execute(new ChargePaymentAction(journal, "U-100", 100).command());
        });

        assertThat(journal).containsExactly(
                "reserve:SKU-001:2",
                "deduct:U-100:50",
                "charge:U-100:100");
    }

    @Test
    void placeOrder_paymentFails_reverseCompensateAndRethrowOriginal() {
        List<String> journal = new ArrayList<>();
        ICompensationManager manager = new LocalCompensationManager();
        IllegalStateException paymentError = new IllegalStateException("支付网关超时");

        // 收款（第三个动作）失败 → 已执行的库存/积分按逆序补偿，原异常被重抛
        assertThatThrownBy(() -> CompensationTemplate.run(manager, scope -> {
            scope.execute(new ReserveInventoryAction(journal, "SKU-001", 2).command());
            scope.execute(new DeductPointsAction(journal, "U-100", 50).command());
            scope.execute(new ChargePaymentAction(journal, "U-100", 100).failOnExecute(paymentError).command());
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
        ICompensationManager manager = new LocalCompensationManager();
        IllegalStateException original = new IllegalStateException("积分服务暂不可用");

        // 正向积分失败 → 已执行的库存补偿也失败 → 抛出 CompensationFailedException（携带原异常为 cause）
        assertThatThrownBy(() -> CompensationTemplate.run(manager, scope -> {
            scope.execute(new ReserveInventoryAction(journal, "SKU-001", 2)
                    .withPolicy(NO_RETRY)
                    .failOnCompensate()
                    .command());
            scope.execute(new DeductPointsAction(journal, "U-100", 50).failOnExecute(original).command());
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
        ICompensationManager manager = new LocalCompensationManager();

        // 实现 IConfirmableAction 的通知动作：提交成功后才收到 confirm（TCC Confirm 阶段）
        String orderId = CompensationTemplate.call(manager, scope -> {
            scope.execute(new ReserveInventoryAction(journal, "SKU-001", 2).command());
            scope.execute(new NotifyOrderCreatedAction(journal, "U-100").command());
            return "SO-20261002-0001";
        });

        assertThat(orderId).isEqualTo("SO-20261002-0001");
        assertThat(journal).containsExactly(
                "reserve:SKU-001:2",
                "notify:U-100",
                "confirm:U-100");
    }

    /** 流程测试用需求：code 为路由编码，ref / amount 承载业务参数（演示参数不在聚合上的场景）。 */
    private record FlowRequirement(String code, String ref, int amount) implements IExternalRequirement {
    }

    /** 预占库存：正向记录占用，逆向释放。 */
    private static class ReserveInventoryAction implements ICompensableAction<TestAggregate, FlowRequirement, String> {
        private final FlowRequirement requirement;
        private final List<String> journal;
        private boolean failOnCompensate;
        private CompensationPolicy policy = CompensationPolicy.defaultPolicy();

        ReserveInventoryAction(List<String> journal, String sku, int qty) {
            this.journal = journal;
            this.requirement = new FlowRequirement("reserve-inventory", sku, qty);
        }

        ReserveInventoryAction failOnCompensate() {
            this.failOnCompensate = true;
            return this;
        }

        ReserveInventoryAction withPolicy(CompensationPolicy policy) {
            this.policy = policy;
            return this;
        }

        CompensationCommand<TestAggregate, FlowRequirement, String> command() {
            return new CompensationCommand<>(this, new TestAggregate(), this.requirement);
        }

        @Override
        public Class<FlowRequirement> requirementType() {
            return FlowRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, FlowRequirement requirement) {
            journal.add("reserve:" + requirement.ref() + ":" + requirement.amount());
            return "RES-" + requirement.ref();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String result) {
            journal.add("release:" + requirement.ref());
            if (failOnCompensate) {
                throw new IllegalStateException("释放库存失败: " + requirement.ref());
            }
        }

        @Override
        public String payload(TestAggregate aggregateRoot, FlowRequirement requirement) {
            return requirement.ref() + ":" + requirement.amount();
        }

        @Override
        public Optional<CompensationPolicy> policy() {
            return Optional.of(policy);
        }
    }

    /** 扣减积分：正向扣减，逆向回退。 */
    private static class DeductPointsAction implements ICompensableAction<TestAggregate, FlowRequirement, String> {
        private final FlowRequirement requirement;
        private final List<String> journal;
        private RuntimeException executeError;

        DeductPointsAction(List<String> journal, String userId, int points) {
            this.journal = journal;
            this.requirement = new FlowRequirement("deduct-points", userId, points);
        }

        DeductPointsAction failOnExecute(RuntimeException error) {
            this.executeError = error;
            return this;
        }

        CompensationCommand<TestAggregate, FlowRequirement, String> command() {
            return new CompensationCommand<>(this, new TestAggregate(), this.requirement);
        }

        @Override
        public Class<FlowRequirement> requirementType() {
            return FlowRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, FlowRequirement requirement) {
            if (executeError != null) {
                throw executeError;
            }
            journal.add("deduct:" + requirement.ref() + ":" + requirement.amount());
            return "PT-" + requirement.ref();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String result) {
            journal.add("restore:" + requirement.ref());
        }

        @Override
        public String payload(TestAggregate aggregateRoot, FlowRequirement requirement) {
            return requirement.ref() + ":" + requirement.amount();
        }
    }

    /** 收款：正向扣款，逆向退款；可配置正向失败。 */
    private static class ChargePaymentAction implements ICompensableAction<TestAggregate, FlowRequirement, String> {
        private final FlowRequirement requirement;
        private final List<String> journal;
        private RuntimeException executeError;

        ChargePaymentAction(List<String> journal, String userId, int amount) {
            this.journal = journal;
            this.requirement = new FlowRequirement("charge-payment", userId, amount);
        }

        ChargePaymentAction failOnExecute(RuntimeException error) {
            this.executeError = error;
            return this;
        }

        CompensationCommand<TestAggregate, FlowRequirement, String> command() {
            return new CompensationCommand<>(this, new TestAggregate(), this.requirement);
        }

        @Override
        public Class<FlowRequirement> requirementType() {
            return FlowRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, FlowRequirement requirement) {
            if (executeError != null) {
                throw executeError;
            }
            journal.add("charge:" + requirement.ref() + ":" + requirement.amount());
            return "PAY-" + requirement.ref();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String result) {
            journal.add("refund:" + requirement.ref());
        }

        @Override
        public String payload(TestAggregate aggregateRoot, FlowRequirement requirement) {
            return requirement.ref() + ":" + requirement.amount();
        }
    }

    /** 下单通知：实现 IConfirmableAction，提交成功后收到 confirm（TCC Confirm）。 */
    private static class NotifyOrderCreatedAction
            implements IConfirmableAction<TestAggregate, FlowRequirement, String> {

        private final FlowRequirement requirement;
        private final List<String> journal;

        NotifyOrderCreatedAction(List<String> journal, String userId) {
            this.journal = journal;
            this.requirement = new FlowRequirement("notify-order", userId, 0);
        }

        CompensationCommand<TestAggregate, FlowRequirement, String> command() {
            return new CompensationCommand<>(this, new TestAggregate(), this.requirement);
        }

        @Override
        public Class<FlowRequirement> requirementType() {
            return FlowRequirement.class;
        }

        @Override
        public Class<TestAggregate> aggregateType() {
            return TestAggregate.class;
        }

        @Override
        public String execute(TestAggregate aggregateRoot, FlowRequirement requirement) {
            journal.add("notify:" + requirement.ref());
            return "MSG-" + requirement.ref();
        }

        @Override
        public void apply(TestAggregate aggregateRoot, String result) {
            // 无回填
        }

        @Override
        public void compensate(TestAggregate aggregateRoot, String result) {
            journal.add("cancel-notify:" + requirement.ref());
        }

        @Override
        public String payload(TestAggregate aggregateRoot, FlowRequirement requirement) {
            return requirement.ref();
        }

        @Override
        public void confirm(TestAggregate aggregateRoot, String result) {
            journal.add("confirm:" + requirement.ref());
        }
    }
}
