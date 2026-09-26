package io.pragmatic.ddd.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KafkaProperties 重试相关配置的取值、退避取档与非法表降级测试。
 *
 * @author wizard-lee
 */
class KafkaPropertiesTest {

    private KafkaProperties config(String inlineRetryIntervalsMs, String retryHopBackoffMs, int maxReconsume) {
        return new KafkaProperties(
                "localhost:9092",
                "g1",
                "",
                500,
                1000,
                "latest",
                "all",
                "",
                true,
                "-dlq",
                maxReconsume,
                1,
                10000,
                "immediate",
                10,
                "-delay",
                inlineRetryIntervalsMs,
                "-retry",
                retryHopBackoffMs,
                30000);
    }

    @Test
    void withDefaultsProvidesRocketMqAlignedRetryWindow() {
        KafkaProperties config = KafkaProperties.withDefaults("localhost:9092", "g1");

        assertThat(config.maxReconsume()).isEqualTo(16);
        assertThat(config.attemptLimit()).isEqualTo(17);
        assertThat(config.inlineRetryTimes()).isEqualTo(2);
        assertThat(config.roundAttempts()).isEqualTo(3);
        assertThat(config.retryTopicSuffix()).isEqualTo("-retry");
        assertThat(config.retryHopBackoffTable()).containsExactly(
                1000, 5000, 10000, 30000, 60000, 120000, 180000, 240000);
        assertThat(config.batchBudgetMs()).isEqualTo(30000);
    }

    @Test
    void attemptLimitIsMaxReconsumePlusOne() {
        assertThat(config("100,500", "1000", 16).attemptLimit()).isEqualTo(17);
        assertThat(config("100,500", "1000", 3).attemptLimit()).isEqualTo(4);
    }

    @Test
    void nonPositiveMaxReconsumeDegradesToSingleAttempt() {
        assertThat(config("100,500", "1000", 0).attemptLimit()).isEqualTo(1);
        assertThat(config("100,500", "1000", -3).attemptLimit()).isEqualTo(1);
    }

    @Test
    void inlineRetryTimesEqualsIntervalTableSize() {
        assertThat(config("100,500", "1000", 16).inlineRetryTimes()).isEqualTo(2);
        assertThat(config("100,500", "1000", 16).roundAttempts()).isEqualTo(3);
        assertThat(config("100,abc,200", "1000", 16).inlineRetryTimes()).isEqualTo(2);
        assertThat(config("1000", "1000", 16).roundAttempts()).isEqualTo(2);
    }

    @Test
    void inlineRetryBackoffTakesRoundPositionAndRestartsEachRound() {
        KafkaProperties config = config("100,500", "1000", 16);

        // 轮内位置：1 → 100ms、2 → 500ms；下一轮（times 4、5）重新从第 1 档起算
        assertThat(config.inlineRetryBackoffMs(1)).isEqualTo(100);
        assertThat(config.inlineRetryBackoffMs(2)).isEqualTo(500);
        assertThat(config.inlineRetryBackoffMs(4)).isEqualTo(100);
        assertThat(config.inlineRetryBackoffMs(5)).isEqualTo(500);
        assertThat(config.inlineRetryBackoffMs(7)).isEqualTo(100);
    }

    @Test
    void retryHopBackoffTakesHopIndexAndClampsToLastEntry() {
        KafkaProperties config = config("100,500", "1000,5000,10000", 16);

        assertThat(config.retryHopBackoffMs(1)).isEqualTo(1000);
        assertThat(config.retryHopBackoffMs(3)).isEqualTo(10000);
        // 超出末档复用末档，hop 从 1 起算（0 与负数按首档兜底）
        assertThat(config.retryHopBackoffMs(4)).isEqualTo(10000);
        assertThat(config.retryHopBackoffMs(9)).isEqualTo(10000);
        assertThat(config.retryHopBackoffMs(0)).isEqualTo(1000);
    }

    @Test
    void emptyOrInvalidTablesDegradeToZeroBackoff() {
        KafkaProperties config = config("", "abc", 16);

        assertThat(config.inlineRetryIntervalTable()).isEmpty();
        assertThat(config.inlineRetryTimes()).isZero();
        assertThat(config.roundAttempts()).isEqualTo(1);
        assertThat(config.inlineRetryBackoffMs(1)).isZero();
        assertThat(config.retryHopBackoffTable()).isEmpty();
        assertThat(config.retryHopBackoffMs(1)).isZero();
    }

    @Test
    void parseMsTableSkipsBlankInvalidAndNonPositiveEntries() {
        assertThat(KafkaProperties.parseMsTable(" 100 , 500 ")).containsExactly(100, 500);
        assertThat(KafkaProperties.parseMsTable("100,abc,-5,,200")).containsExactly(100, 200);
        assertThat(KafkaProperties.parseMsTable(" , ")).isEmpty();
        assertThat(KafkaProperties.parseMsTable(null)).isEmpty();
    }
}
