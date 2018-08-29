package brave.jms;

import java.io.Serializable;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.XASession;

class TracingSession implements Session {
  static Session create(Session delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (delegate instanceof TracingSession) return delegate;
    if (delegate instanceof QueueSession) {
      return TracingQueueSession.create((QueueSession) delegate, jmsTracing);
    }
    if (delegate instanceof TopicSession) {
      return TracingTopicSession.create((TopicSession) delegate, jmsTracing);
    }
    if (delegate instanceof XASession) {
      return TracingXASession.create((XASession) delegate, jmsTracing);
    }
    return new TracingSession(delegate, jmsTracing);
  }

  final Session delegate;
  final JmsTracing jmsTracing;

  TracingSession(Session delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public BytesMessage createBytesMessage() throws JMSException {
    return delegate.createBytesMessage();
  }

  @Override public MapMessage createMapMessage() throws JMSException {
    return delegate.createMapMessage();
  }

  @Override public Message createMessage() throws JMSException {
    return delegate.createMessage();
  }

  @Override public ObjectMessage createObjectMessage() throws JMSException {
    return delegate.createObjectMessage();
  }

  @Override public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
    return delegate.createObjectMessage(object);
  }

  @Override public StreamMessage createStreamMessage() throws JMSException {
    return delegate.createStreamMessage();
  }

  @Override public TextMessage createTextMessage() throws JMSException {
    return delegate.createTextMessage();
  }

  @Override public TextMessage createTextMessage(String text) throws JMSException {
    return delegate.createTextMessage(text);
  }

  @Override public boolean getTransacted() throws JMSException {
    return delegate.getTransacted();
  }

  @Override public int getAcknowledgeMode() throws JMSException {
    return delegate.getAcknowledgeMode();
  }

  @Override public void commit() throws JMSException {
    delegate.commit();
  }

  @Override public void rollback() throws JMSException {
    delegate.rollback();
  }

  @Override public void close() throws JMSException {
    delegate.close();
  }

  @Override public void recover() throws JMSException {
    delegate.recover();
  }

  @Override public MessageListener getMessageListener() throws JMSException {
    return delegate.getMessageListener();
  }

  @Override public void setMessageListener(MessageListener listener) throws JMSException {
    delegate.setMessageListener(TracingMessageListener.create(listener, jmsTracing));
  }

  @Override public void run() {
    delegate.run();
  }

  @Override public MessageProducer createProducer(Destination destination) throws JMSException {
    return TracingMessageProducer.create(delegate.createProducer(destination), jmsTracing);
  }

  @Override public MessageConsumer createConsumer(Destination destination) throws JMSException {
    return TracingMessageConsumer.create(delegate.createConsumer(destination), jmsTracing);
  }

  @Override public MessageConsumer createConsumer(Destination destination, String messageSelector)
      throws JMSException {
    MessageConsumer mc = delegate.createConsumer(destination, messageSelector);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  @Override public MessageConsumer createConsumer(Destination destination, String messageSelector,
      boolean noLocal) throws JMSException {
    MessageConsumer mc = delegate.createConsumer(destination, messageSelector, noLocal);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName)
      throws JMSException {
    MessageConsumer mc = delegate.createSharedConsumer(topic, sharedSubscriptionName);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName,
      String messageSelector) throws JMSException {
    MessageConsumer mc =
        delegate.createSharedConsumer(topic, sharedSubscriptionName, messageSelector);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  @Override public Queue createQueue(String queueName) throws JMSException {
    return delegate.createQueue(queueName);
  }

  @Override public Topic createTopic(String topicName) throws JMSException {
    return delegate.createTopic(topicName);
  }

  @Override public TopicSubscriber createDurableSubscriber(Topic topic, String name)
      throws JMSException {
    TopicSubscriber ts = delegate.createDurableSubscriber(topic, name);
    return TracingTopicSubscriber.create(ts, jmsTracing);
  }

  @Override
  public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector,
      boolean noLocal) throws JMSException {
    TopicSubscriber ts = delegate.createDurableSubscriber(topic, name, messageSelector, noLocal);
    return TracingTopicSubscriber.create(ts, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public MessageConsumer createDurableConsumer(Topic topic, String name)
      throws JMSException {
    return TracingMessageConsumer.create(delegate.createDurableConsumer(topic, name), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public MessageConsumer createDurableConsumer(Topic topic, String name,
      String messageSelector,
      boolean noLocal) throws JMSException {
    MessageConsumer mc = delegate.createDurableConsumer(topic, name, messageSelector, noLocal);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public MessageConsumer createSharedDurableConsumer(Topic topic, String name)
      throws JMSException {
    MessageConsumer mc = delegate.createSharedDurableConsumer(topic, name);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public MessageConsumer createSharedDurableConsumer(Topic topic, String name,
      String messageSelector) throws JMSException {
    MessageConsumer mc = delegate.createSharedDurableConsumer(topic, name, messageSelector);
    return TracingMessageConsumer.create(mc, jmsTracing);
  }

  @Override public QueueBrowser createBrowser(Queue queue) throws JMSException {
    return delegate.createBrowser(queue);
  }

  @Override public QueueBrowser createBrowser(Queue queue, String messageSelector)
      throws JMSException {
    return delegate.createBrowser(queue, messageSelector);
  }

  @Override public TemporaryQueue createTemporaryQueue() throws JMSException {
    return delegate.createTemporaryQueue();
  }

  @Override public TemporaryTopic createTemporaryTopic() throws JMSException {
    return delegate.createTemporaryTopic();
  }

  @Override public void unsubscribe(String name) throws JMSException {
    delegate.unsubscribe(name);
  }
}
