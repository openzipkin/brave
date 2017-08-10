package brave.kafka;

import brave.Tracing;
import brave.sampler.Sampler;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.Future;
import kafka.admin.AdminUtils;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Time;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ITTracingKafka {

  String LOCALHOST = "127.0.0.1";
  String KAFKA_PORT = "9092";

  String TEST_TOPIC = "myTopic";
  String TEST_KEY = "foo";
  String TEST_VALUE = "bar";

  KafkaServer kafkaServer;
  ZkUtils zkUtils;

  Tracing consumerTracing;
  Tracing producerTracing;

  LinkedList<Span> consumerSpans = new LinkedList<>();
  LinkedList<Span> producerSpans = new LinkedList<>();

  @Before
  public void setUp() throws Exception {
    consumerTracing = Tracing.newBuilder()
        .reporter(consumerSpans::add)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();
    producerTracing = Tracing.newBuilder()
        .reporter(producerSpans::add)
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();

    initKafkaServer();
  }

  @After
  public void closeKafkaServer() {
    kafkaServer.shutdown();
  }

  @Test
  public void produce_and_consume_kafka_message() throws Exception {
    Producer<String, String> tracingProducer = createTracingProducer();
    Consumer<String, String> tracingConsumer = createTracingConsumer();

    Future<RecordMetadata> send =
        tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
    // Block for synchronous send
    send.get();

    ConsumerRecords<String, String> records = null;
    Long startTime = System.currentTimeMillis();
    Long elapsedTime = 0L;
    final Long pollTimeout = 10 * 1000L; // 10 sec
    while (elapsedTime < pollTimeout) {
      records = tracingConsumer.poll(1000);
      elapsedTime = startTime - System.currentTimeMillis();
      if (!records.isEmpty()) {
        // No need to poll more, we have the records
        break;
      }
    }

    assertThat(elapsedTime).isLessThan(pollTimeout);

    assertThat(records).hasSize(1);
    assertThat(producerSpans).hasSize(1);
    assertThat(consumerSpans).hasSize(1);

    assertThat(Long.toHexString(consumerSpans.getFirst().traceId))
        .isEqualTo(Long.toHexString(producerSpans.getFirst().traceId));

    RecordTracing recordTracing = new RecordTracing(consumerTracing);
    for (ConsumerRecord<String, String> record : records) {
      brave.Span span = recordTracing.nextSpan(record);
      assertThat(span.context().parentId()).isEqualTo(producerSpans.getLast().traceId);
    }
  }

  void initKafkaServer() throws IOException {
    String zkAddress = setupZookeeper();
    setupKafka(zkAddress);
  }

  String setupZookeeper() {
    EmbeddedZookeeper zkServer = new EmbeddedZookeeper();
    String zkAddress = LOCALHOST + ":" + zkServer.port();
    ZkClient zkClient = new ZkClient(zkAddress, 30000, 30000, ZKStringSerializer$.MODULE$);
    zkUtils = ZkUtils.apply(zkClient, false);
    return zkAddress;
  }

  void setupKafka(String zkAddress) throws IOException {
    Properties brokerProps = new Properties();
    brokerProps.setProperty("zookeeper.connect", zkAddress);
    brokerProps.setProperty("broker.id", "0");
    brokerProps.setProperty("log.dirs",
        Files.createTempDirectory("kafka-").toAbsolutePath().toString());
    brokerProps.setProperty("listeners", "PLAINTEXT://" + LOCALHOST + ":" + KAFKA_PORT);

    brokerProps.setProperty("replica.socket.timeout.ms", "1000");
    brokerProps.setProperty("controller.socket.timeout.ms", "1000");
    brokerProps.setProperty("offsets.topic.replication.factor", "1");
    brokerProps.setProperty("offsets.topic.num.partitions", "1");

    KafkaConfig config = new KafkaConfig(brokerProps);

    kafkaServer = TestUtils.createServer(config, Time.SYSTEM);
    kafkaServer.startup();
    AdminUtils.createTopic(
        zkUtils, TEST_TOPIC, 1, 1, new Properties(), null);
  }

  Consumer<String, String> createTracingConsumer() {
    KafkaConsumer<String, String> consumer = new KafkaConsumer<>(getDefaultConsumerProperties());
    consumer.assign(Collections.singleton(new TopicPartition(TEST_TOPIC, 0)));
    return KafkaTracing.create(consumerTracing).consumer(consumer);
  }

  Producer<String, String> createTracingProducer() {
    KafkaProducer<String, String> producer = new KafkaProducer<>(getDefaultProducerProperties());
    return KafkaTracing.create(producerTracing).producer(producer);
  }

  Properties getDefaultProducerProperties() {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", LOCALHOST + ":" + KAFKA_PORT);
    properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    return properties;
  }

  Properties getDefaultConsumerProperties() {
    Properties properties = new Properties();
    properties.put("bootstrap.servers", LOCALHOST + ":" + KAFKA_PORT);
    properties.put("group.id", "test");
    properties.put("client.id", "test-0");
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put("value.deserializer",
        "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put("auto.offset.reset", "earliest");
    return properties;
  }
}