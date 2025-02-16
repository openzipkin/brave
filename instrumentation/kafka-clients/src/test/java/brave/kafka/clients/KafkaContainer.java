/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import java.util.Properties;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.testcontainers.utility.DockerImageName.parse;

final class KafkaContainer extends GenericContainer<KafkaContainer> {
  static final Logger LOGGER = LoggerFactory.getLogger(KafkaContainer.class);
  static final int KAFKA_PORT = 19092;

  KafkaContainer() {
    super(parse("ghcr.io/openzipkin/zipkin-kafka:3.4.3"));
    waitStrategy = Wait.forHealthcheck();
    // Kafka broker listener port (19092) needs to be exposed for test cases to access it.
    addFixedExposedPort(KAFKA_PORT, KAFKA_PORT, InternetProtocol.TCP);
    withLogConsumer(new Slf4jLogConsumer(LOGGER));
  }

  String bootstrapServer() {
    return getHost() + ":" + getMappedPort(KAFKA_PORT);
  }

  KafkaProducer<String, String> createStringProducer() {
    return new KafkaProducer<>(producerConfig(), new StringSerializer(), new StringSerializer());
  }

  Properties producerConfig() {
    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServer());
    props.put("acks", "1");
    props.put("batch.size", "10");
    props.put("client.id", "kafka-extension");
    props.put("request.timeout.ms", "500");
    return props;
  }

  KafkaConsumer<String, String> createStringConsumer() {
    return new KafkaConsumer<>(consumerConfig(), new StringDeserializer(),
      new StringDeserializer());
  }

  Properties consumerConfig() {
    Properties props = new Properties();
    props.put("bootstrap.servers", bootstrapServer());
    props.put("group.id", "kafka-extension");
    props.put("enable.auto.commit", "true");
    props.put("auto.commit.interval.ms", "10");
    props.put("auto.offset.reset", "earliest");
    props.put("heartbeat.interval.ms", "100");
    props.put("session.timeout.ms", "200");
    props.put("fetch.max.wait.ms", "200");
    props.put("metadata.max.age.ms", "100");
    return props;
  }
}
