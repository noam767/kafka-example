package com.example.kafka;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumerApp {

    private static final Logger log = LoggerFactory.getLogger(ConsumerApp.class);

    public static void main(String[] args) {
        String bootstrapServers = System.getenv().getOrDefault(
                "KAFKA_BOOTSTRAP_SERVERS",
                "demo-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092");
        String topic = System.getenv().getOrDefault("KAFKA_TOPIC", "demo-topic");
        String groupId = System.getenv().getOrDefault("KAFKA_GROUP_ID", "demo-consumer-group");
        String podName = System.getenv().getOrDefault("HOSTNAME", "consumer-unknown");

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, podName);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "5000");

        AtomicLong counter = new AtomicLong(0);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                log.info("Shutting down consumer {}, total consumed: {}", podName, counter.get())));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            log.info("Consumer {} started, bootstrap: {}, topic: {}, group: {}",
                    podName, bootstrapServers, topic, groupId);

            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    long seq = counter.incrementAndGet();
                    log.info("Consumed #{}: partition={} offset={} key={} value={}",
                            seq, record.partition(), record.offset(), record.key(), record.value());
                }
            }
        }
    }
}
