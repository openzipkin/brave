package brave.kafka.streams;

import brave.kafka.clients.KafkaTracing;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * Tagging policy is not yet dynamic. The descriptions below reflect static policy.
 */
class KafkaStreamsTags {
    /**
     * Added on {@link KafkaStreamsTracing#nextSpan(ProcessorContext)} when the key not null or empty.
     */
    static final String KAFKA_STREAMS_KEY_TAG = "kafka.streams.key";
    static final String KAFKA_STREAMS_APPLICATION_ID_TAG = "kafka.streams.application.id";
    static final String KAFKA_STREAMS_TASK_ID_TAG = "kafka.streams.task.id";
}
