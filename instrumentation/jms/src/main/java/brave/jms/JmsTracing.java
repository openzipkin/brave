package brave.jms;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;

import static brave.propagation.B3SingleFormat.writeB3SingleFormatWithoutParentId;

/** Use this class to decorate your Jms consumer / producer and enable Tracing. */
public final class JmsTracing {
  static final String JMS_QUEUE = "jms.queue";
  static final String JMS_TOPIC = "jms.topic";

  static final Getter<Message, String> GETTER = new Getter<Message, String>() {
    @Override public String get(Message carrier, String key) {
      try {
        return carrier.getStringProperty(key);
      } catch (JMSException e) {
        // don't crash on wonky exceptions!
        return null;
      }
    }

    @Override public String toString() {
      return "Message::getStringProperty";
    }
  };

  public static JmsTracing create(Tracing tracing) {
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    String remoteServiceName = "jms";

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    /**
     * The remote service name that describes the broker in the dependency graph. Defaults to "jms"
     */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    public JmsTracing build() {
      return new JmsTracing(this);
    }
  }

  final Tracing tracing;
  final Extractor<Message> extractor;
  final String remoteServiceName;
  final Set<String> propagationKeys;

  JmsTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(GETTER);
    this.remoteServiceName = builder.remoteServiceName;
    this.propagationKeys = new LinkedHashSet<>(tracing.propagation().keys());
  }

  public MessageConsumer messageConsumer(MessageConsumer messageConsumer) {
    if (messageConsumer == null) throw new NullPointerException("messageConsumer == null");
    if (messageConsumer instanceof QueueReceiver) {
      return new TracingQueueReceiver((QueueReceiver) messageConsumer, this);
    }
    if (messageConsumer instanceof TopicSubscriber) {
      return new TracingTopicSubscriber((TopicSubscriber) messageConsumer, this);
    }
    return new TracingMessageConsumer(messageConsumer, this);
  }

  public MessageProducer messageProducer(MessageProducer messageProducer) {
    if (messageProducer == null) throw new NullPointerException("messageProducer == null");
    if (messageProducer instanceof QueueSender) {
      return new TracingQueueSender((QueueSender) messageProducer, this);
    }
    if (messageProducer instanceof TopicPublisher) {
      return new TracingTopicPublisher((TopicPublisher) messageProducer, this);
    }
    return new TracingMessageProducer(messageProducer, this);
  }

  /**
   * Use this to create a span for processing the given message. Note: the result has no name and is
   * not started.
   *
   * <p>This creates a child from identifiers extracted from the message message, or a new span if
   * one couldn't be extracted.
   */
  public Span nextSpan(Message message) {
    TraceContextOrSamplingFlags extracted = extractAndClearMessage(message);
    Span result = tracing.tracer().nextSpan(extracted);

    // When an upstream context was not present, lookup keys are unlikely added
    if (extracted.context() == null && !result.isNoop()) {
      tagQueueOrTopic(message, result);
    }
    return result;
  }

  TraceContextOrSamplingFlags extractAndClearMessage(Message message) {
    TraceContextOrSamplingFlags extracted = extractor.extract(message);
    // Clear propagation regardless of extraction as JMS requires clearing as a means to make the
    // message writable
    PropertyFilter.MESSAGE.filterProperties(message, propagationKeys);
    return extracted;
  }

  /** This is only safe to call after {@link JmsTracing#extractAndClearMessage(Message)} */
  static void addB3SingleHeader(TraceContext context, Message message) {
    try {
      message.setStringProperty("b3", writeB3SingleFormatWithoutParentId(context));
    } catch (JMSException ignored) {
      // don't crash on wonky exceptions!
    }
  }

  void tagQueueOrTopic(Message message, SpanCustomizer span) {
    try {
      Destination destination = message.getJMSDestination();
      if (destination != null) tagQueueOrTopic(destination, span);
    } catch (JMSException e) {
      // don't crash on wonky exceptions!
    }
  }

  void tagQueueOrTopic(Destination destination, SpanCustomizer span) {
    try {
      if (destination instanceof Queue) {
        span.tag(JMS_QUEUE, ((Queue) destination).getQueueName());
      } else if (destination instanceof Topic) {
        span.tag(JMS_TOPIC, ((Topic) destination).getTopicName());
      }
    } catch (JMSException ignored) {
      // don't crash on wonky exceptions!
    }
  }
}
