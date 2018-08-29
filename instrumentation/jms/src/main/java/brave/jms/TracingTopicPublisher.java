package brave.jms;

import brave.Span;
import brave.Tracer.SpanInScope;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

final class TracingTopicPublisher extends TracingMessageProducer implements TopicPublisher {
  static TopicPublisher create(TopicPublisher delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("topicPublisher == null");
    if (delegate instanceof TracingTopicPublisher) return delegate;
    return new TracingTopicPublisher(delegate, jmsTracing);
  }

  final TopicPublisher tp;

  TracingTopicPublisher(TopicPublisher delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    tp = delegate;
  }

  @Override public Topic getTopic() throws JMSException {
    return tp.getTopic();
  }

  @Override public void publish(Message message) throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      tp.publish(message);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  @Override public void publish(Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      tp.publish(message, deliveryMode, priority, timeToLive);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  @Override public void publish(Topic topic, Message message) throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      tp.publish(topic, message);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }

  @Override
  public void publish(Topic topic, Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      tp.publish(topic, message, deliveryMode, priority, timeToLive);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }
}
