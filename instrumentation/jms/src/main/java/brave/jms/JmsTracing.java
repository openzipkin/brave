/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jms;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.jms.JmsAdapter.MessageAdapter;
import brave.messaging.ConsumerHandler;
import brave.messaging.MessagingAdapter;
import brave.messaging.MessagingParser;
import brave.messaging.MessagingTracing;
import brave.messaging.ProcessorHandler;
import brave.messaging.ProducerHandler;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
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
  static final Setter<Message, String> SETTER = new Setter<Message, String>() {
    @Override public void put(Message carrier, String key, String value) {
      try {
        carrier.setStringProperty(key, value);
      } catch (JMSException e) {
        // don't crash on wonky exceptions!
      }
    }

    @Override public String toString() {
      return "Message::setStringProperty";
    }
  };

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
    final MessagingTracing messageTracing;
    String remoteServiceName = "jms";

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.messageTracing = MessagingTracing.newBuilder(tracing)
        .parser(new LegacyMessagingParser()).build();
    }

    Builder(MessagingTracing messageTracing) {
      if (messageTracing == null) throw new NullPointerException("messageTracing == null");
      this.messageTracing = messageTracing;
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

  final MessagingTracing messageTracing;
  final ConsumerHandler<Destination, Message, Message> consumerHandler;
  final ProcessorHandler<Destination, Message, Message> processorHandler;
  final ProducerHandler<Destination, Message, Message> producerHandler;
  final Extractor<Message> messageExtractor;
  final Injector<Message> messageInjector;
  final MessageAdapter messageAdapter;
  final String remoteServiceName;
  final Set<String> propagationKeys;

  JmsTracing(Builder builder) { // intentionally hidden constructor
    this.messageTracing = builder.messageTracing;
    this.messageExtractor = messageTracing.tracing().propagation().extractor(GETTER);
    this.remoteServiceName = builder.remoteServiceName;
    this.propagationKeys = new LinkedHashSet<>(messageTracing.tracing().propagation().keys());
    this.messageAdapter = new MessageAdapter(this);
    this.messageInjector = new FilteringInjector<>(PropertyFilter.MESSAGE, propagationKeys, SETTER);
    consumerHandler =
      ConsumerHandler.create(messageTracing, messageAdapter, messageExtractor, messageInjector);
    processorHandler = ProcessorHandler.create(messageTracing, consumerHandler);
    producerHandler =
      ProducerHandler.create(messageTracing, messageAdapter, messageExtractor, messageInjector);
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
    return processorHandler.startProcessor(destination(message), message, false);
  }

  //TraceContextOrSamplingFlags extractAndClearMessage(Message message) {
  //  TraceContextOrSamplingFlags extracted = extractor.extract(message);
  //  // Clear propagation regardless of extraction as JMS requires clearing as a means to make the
  //  // message writable
  //  PropertyFilter.MESSAGE.filterProperties(message, propagationKeys);
  //  return extracted;
  //}

  @Nullable static Destination destination(Message message) {
    try {
      return message.getJMSDestination();
    } catch (JMSException e) {
      // don't crash on wonky exceptions!
    }
    return null;
  }

  static class FilteringInjector<C> implements TraceContext.Injector<C> {
    final PropertyFilter filter;
    final Set<String> namesToClear;
    final Setter<C, String> setter;

    FilteringInjector(PropertyFilter filter, Set<String> namesToClear, Setter<C, String> setter) {
      this.filter = filter;
      this.namesToClear = namesToClear;
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      filter.filterProperties(carrier, namesToClear);
      setter.put(carrier, "b3", writeB3SingleFormatWithoutParentId(traceContext));
    }

    @Override public String toString() {
      return setter + "(\"b3\",singleHeaderFormatWithoutParent)";
    }
  }

  static class LegacyMessagingParser extends MessagingParser {
    @Override
    protected <Chan, Msg, C> void addMessageTags(MessagingAdapter<Chan, Msg, C> adapter,
      Chan channel, @Nullable Msg msg, TraceContext context, SpanCustomizer customizer) {
      String channelName = adapter.channel(channel);
      if (channelName == null) return;
      if (channel instanceof Queue) {
        customizer.tag("jms.queue", channelName);
      } else if (channel instanceof Topic) {
        customizer.tag("jms.topic", channelName);
      }
    }
  }
}
