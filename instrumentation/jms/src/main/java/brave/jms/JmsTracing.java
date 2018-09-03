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
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;

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

  public Connection connection(Connection connection) {
    // It is common to implement both interfaces
    if (connection instanceof XAConnection) {
      return xaConnection((XAConnection) connection);
    }
    return TracingConnection.create(connection, this);
  }

  public QueueConnection queueConnection(QueueConnection connection) {
    // It is common to implement both interfaces
    if (connection instanceof XAQueueConnection) {
      return xaQueueConnection((XAQueueConnection) connection);
    }
    return TracingConnection.create(connection, this);
  }

  public TopicConnection topicConnection(TopicConnection connection) {
    // It is common to implement both interfaces
    if (connection instanceof XATopicConnection) {
      return xaTopicConnection((XATopicConnection) connection);
    }
    return TracingConnection.create(connection, this);
  }

  public XAConnection xaConnection(XAConnection xaConnection) {
    return TracingXAConnection.create(xaConnection, this);
  }

  public XAQueueConnection xaQueueConnection(XAQueueConnection connection) {
    return TracingXAConnection.create(connection, this);
  }

  public XATopicConnection xaTopicConnection(XATopicConnection connection) {
    return TracingXAConnection.create(connection, this);
  }

  public ConnectionFactory connectionFactory(ConnectionFactory connectionFactory) {
    // It is common to implement both interfaces
    if (connectionFactory instanceof XAConnectionFactory) {
      return (ConnectionFactory) xaConnectionFactory((XAConnectionFactory) connectionFactory);
    }
    return TracingConnectionFactory.create(connectionFactory, this);
  }

  public XAConnectionFactory xaConnectionFactory(XAConnectionFactory xaConnectionFactory) {
    return TracingXAConnectionFactory.create(xaConnectionFactory, this);
  }

  /**
   * Returns a message listener that optionally starts a consumer span for the message received
   * before wrapping the listener in a separate span.
   *
   * @param messageListener listener to wrap
   * @param addConsumerSpan set to true when the underlying message receipt is not traced (ex. JCA)
   */
  public MessageListener messageListener(MessageListener messageListener, boolean addConsumerSpan) {
    if (messageListener instanceof TracingMessageListener) return messageListener;
    return new TracingMessageListener(messageListener, this, addConsumerSpan);
  }

  /**
   * Use this to create a span for processing the given message. Note: the result has no name and is
   * not started.
   *
   * <p>In general, prefer {@link MessageListener} for processing messages, as it is more efficient
   * and less lossy with regards to context data.
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

  /**
   * We currently serialize the context as a "b3" message property. We can't add the context as an
   * Object property because JMS only supports "primitive objects, String, Map and List types".
   *
   * <p>Using {@link MessageListener} is preferred where possible, as we can coordinate without
   * writing "b3", which notably saves us from losing data in {@link TraceContext#extra()}.
   */
  void setNextParent(Message message, TraceContext context) {
    addB3SingleHeader(message, context);
  }

  /** This is only safe to call after {@link JmsTracing#extractAndClearMessage(Message)} */
  static void addB3SingleHeader(Message message, TraceContext context) {
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
