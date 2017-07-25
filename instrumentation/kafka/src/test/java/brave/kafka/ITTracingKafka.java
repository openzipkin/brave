package brave.kafka;

import brave.Tracing;
import brave.sampler.Sampler;
import kafka.admin.AdminUtils;
import kafka.admin.RackAwareMode;
import kafka.server.KafkaConfig;
import kafka.server.KafkaConfig$;
import kafka.server.KafkaServer;
import kafka.utils.MockTime;
import kafka.utils.TestUtils;
import kafka.utils.ZKStringSerializer$;
import kafka.utils.ZkUtils;
import kafka.zk.EmbeddedZookeeper;
import org.I0Itec.zkclient.ZkClient;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.utils.Time;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Future;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ITTracingKafka {

    private static final String LOCALHOST = "127.0.0.1";
    private static final String KAFKA_PORT = "9092";

    private static final String TEST_TOPIC = "myTopic";
    private static final String TEST_KEY = "foo";
    private static final String TEST_VALUE = "bar";

    private EmbeddedZookeeper zkServer;
    private KafkaServer kafkaServer;
    private ZkUtils zkUtils;

    private ConcurrentLinkedDeque<Span> consumerSpans = new ConcurrentLinkedDeque<>();
    private ConcurrentLinkedDeque<Span> producerSpans = new ConcurrentLinkedDeque<>();

    @Before
    public void initKafkaServer() throws IOException {
        String zkAddress = setupZookeeper();
        setupKafka(zkAddress);
    }

    @After
    public void closeKafkaServer() {
        kafkaServer.shutdown();
        zkServer.shutdown();
    }

    @Test
    public void produce_and_consume_kafka_message() throws Exception {
        TracingProducer<String, String> tracingProducer = createTracingProducer();
        TracingConsumer<String, String> tracingConsumer = createTracingConsumer();

        Future<RecordMetadata> send = tracingProducer.send(new ProducerRecord<>(TEST_TOPIC, TEST_KEY, TEST_VALUE));
        // Block for synchronous send
        send.get();

        ConsumerRecords<String, String> poll = tracingConsumer.poll(1000);

        assertThat(poll).hasSize(1);
        assertThat(Long.toHexString(consumerSpans.getFirst().traceId))
                .isEqualTo(Long.toHexString(producerSpans.getFirst().traceId));
    }

    private TracingConsumer<String, String> createTracingConsumer() {
        Tracing consumerTracing = Tracing.newBuilder()
                .reporter(consumerSpans::add)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .build();

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(getDefaultConsumerProperties());
        consumer.assign(Collections.singleton(new TopicPartition(TEST_TOPIC, 0)));
        return new TracingConsumer<>(consumerTracing, consumer);
    }

    private TracingProducer<String, String> createTracingProducer() {
        Tracing producerTracing = Tracing.newBuilder()
                .reporter(producerSpans::add)
                .sampler(Sampler.ALWAYS_SAMPLE)
                .build();

        KafkaProducer<String, String> producer = new KafkaProducer<>(getDefaultProducerProperties());
        return new TracingProducer<>(producerTracing, producer);
    }

    private String setupZookeeper() {
        zkServer = new EmbeddedZookeeper();
        String zkAddress = LOCALHOST + ":" + zkServer.port();
        ZkClient zkClient = new ZkClient(zkAddress, 30000, 30000, ZKStringSerializer$.MODULE$);
        zkUtils = ZkUtils.apply(zkClient, false);
        return zkAddress;
    }

    private void setupKafka(String zkAddress) throws IOException {
        Properties brokerProps = new Properties();
        brokerProps.setProperty("zookeeper.connect", zkAddress);
        brokerProps.setProperty("broker.id", "0");
        brokerProps.setProperty("log.dirs", Files.createTempDirectory("kafka-").toAbsolutePath().toString());
        brokerProps.setProperty("listeners", "PLAINTEXT://" + LOCALHOST + ":" + KAFKA_PORT);

        brokerProps.setProperty("replica.socket.timeout.ms", "1000");
        brokerProps.setProperty("controller.socket.timeout.ms", "1000");
        brokerProps.setProperty("offsets.topic.replication.factor", "1");

        KafkaConfig config = new KafkaConfig(brokerProps);

        kafkaServer = TestUtils.createServer(config, Time.SYSTEM);
        kafkaServer.startup();
        AdminUtils.createTopic(
                zkUtils, TEST_TOPIC, 1, 1, new Properties(), null);
    }

    private Properties getDefaultProducerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", LOCALHOST + ":" + KAFKA_PORT);
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        return properties;
    }

    private Properties getDefaultConsumerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", LOCALHOST + ":" + KAFKA_PORT);
        properties.put("group.id", "test");
        properties.put("client.id", "test-0");
        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("auto.offset.reset", "earliest");
        return properties;
    }
}