package com.example.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

public class ProducerApp {

    private static final Logger log = LoggerFactory.getLogger(ProducerApp.class);

    public static void main(String[] args) {
        String bootstrapServers = System.getenv().getOrDefault(
                "KAFKA_BOOTSTRAP_SERVERS",
                "demo-kafka-kafka-bootstrap.kafka.svc.cluster.local:9092");
        String topic = System.getenv().getOrDefault("KAFKA_TOPIC", "demo-topic");
        String podName = System.getenv().getOrDefault("HOSTNAME", "producer-unknown");
        int intervalMs = Integer.parseInt(
                System.getenv().getOrDefault("PRODUCE_INTERVAL_MS", "2000"));

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, podName);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);

        AtomicLong counter = new AtomicLong(0);

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                log.info("Shutting down producer {}, total sent: {}", podName, counter.get())));

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            log.info("Producer {} started, bootstrap: {}, topic: {}", podName, bootstrapServers, topic);

            while (!Thread.currentThread().isInterrupted()) {
                long seq = counter.incrementAndGet();
                String key = podName + "-" + seq;
                String value = String.format("{\"producer\":\"%s\",\"seq\":%d,\"ts\":%d}",
                        podName, seq, System.currentTimeMillis());

                ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        log.error("Send failed for key={}: {}", key, exception.getMessage());
                    } else {
                        log.info("Sent key={} to partition={} offset={}",
                                key, metadata.partition(), metadata.offset());
                    }
                });

                Thread.sleep(intervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Producer {} interrupted", podName);
        }
    }
}
