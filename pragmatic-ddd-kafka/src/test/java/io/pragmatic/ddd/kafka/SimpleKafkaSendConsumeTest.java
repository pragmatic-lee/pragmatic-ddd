package io.pragmatic.ddd.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("integration")
public class SimpleKafkaSendConsumeTest {
    private static final String TOPIC_RT = "pdd_simple_kafka_test";

    @BeforeAll
    static void requireBroker() {
        assumeTrue(KafkaTestSupport.isAvailable(),
                "跳过真实 Kafka 集成测试：未检测到本地 Kafka（" + KafkaTestSupport.bootstrapServers()
                        + "），可用 -Dkafka.bootstrap-servers=host:port 指定");
    }

    @Test
    public void publish_message() throws ExecutionException, InterruptedException, TimeoutException {

        KafkaProducer<String, byte[]> stringKafkaProducer = this.stringKafkaProducer();


        ProducerRecord<String, byte[]> test = new ProducerRecord<>(TOPIC_RT,
                null,
                "test",
                "test".getBytes(StandardCharsets.UTF_8));

        stringKafkaProducer.send(test).get(1000, TimeUnit.SECONDS);
        stringKafkaProducer.send(test).get(1000, TimeUnit.SECONDS);
        stringKafkaProducer.send(test).get(1000, TimeUnit.SECONDS);
        stringKafkaProducer.send(test).get(1000, TimeUnit.SECONDS);
        stringKafkaProducer.send(test).get(1000, TimeUnit.SECONDS);


        List<KafkaConsumer<String, byte[]>> kafkaConsumerList = new ArrayList<>();


        var countdown = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            KafkaConsumer<String, byte[]> kafkaConsumer = this.createKafkaConsumer();
            kafkaConsumerList.add(kafkaConsumer);

            kafkaConsumer.subscribe(Stream.of(TOPIC_RT).collect(Collectors.toList()));
            boolean hasPolled = false;

            while (!hasPolled) {
                ConsumerRecords<String, byte[]> records = kafkaConsumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, byte[]> record : records) {

                    var key = record.key();
                    var value = new String(record.value(), StandardCharsets.UTF_8);
                    var topic = record.topic();
                    var offset = record.offset();
                    var partition = record.partition();

                    System.out.println(MessageFormat.format("key = {0}," +
                            "value = {1},topic = {2}, " +
                            "offset = {3}, partition = {4}", key, value, topic, offset, partition));
                }

                hasPolled = true;

                if (!records.isEmpty()) {
                    kafkaConsumer.commitAsync();
                }
                countdown.countDown();
            }
        });

        thread.start();

        boolean await = countdown.await(30, TimeUnit.SECONDS);

        assertThat(await).isTrue();
        kafkaConsumerList.forEach(KafkaConsumer::close);
        stringKafkaProducer.close();
    }


    private KafkaProducer<String, byte[]> stringKafkaProducer() {

        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_DOC, true);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        return new KafkaProducer<>(properties);
    }

    private KafkaConsumer<String, byte[]> createKafkaConsumer() {

        Properties properties = new Properties();

        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "simple_test");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);


        return new KafkaConsumer<>(properties);

    }

}
