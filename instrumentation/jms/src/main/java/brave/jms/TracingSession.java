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
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicSession;

import static brave.jms.TracingConnection.TYPE_QUEUE;
import static brave.jms.TracingConnection.TYPE_TOPIC;
import static brave.jms.TracingConnection.TYPE_XA;
import static brave.jms.TracingConnection.TYPE_XA_QUEUE;
import static brave.jms.TracingConnection.TYPE_XA_TOPIC;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
class TracingSession implements QueueSession, TopicSession {

  static TracingSession create(Session delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingSession) return (TracingSession) delegate;
    return new TracingSession(delegate, jmsTracing);
  }

  final Session delegate;
  final JmsTracing jmsTracing;
  final int types;

  TracingSession(Session delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    int types = 0;
    if (delegate instanceof QueueSession) types |= TYPE_QUEUE;
    if (delegate instanceof TopicSession) types |= TYPE_TOPIC;
    if (delegate instanceof XASession) types |= TYPE_XA;
    if (delegate instanceof XAQueueSession) types |= TYPE_XA_QUEUE;
    if (delegate instanceof XATopicSession) types |= TYPE_XA_TOPIC;
    this.types = types;
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
    return TracingMessageConsumer.create(ts, jmsTracing);
  }

  @Override
  public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector,
    boolean noLocal) throws JMSException {
    TopicSubscriber ts = delegate.createDurableSubscriber(topic, name, messageSelector, noLocal);
    return TracingMessageConsumer.create(ts, jmsTracing);
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

  // QueueSession

  @Override public QueueReceiver createReceiver(Queue queue) throws JMSException {
    checkQueueSession();
    QueueSession qs = (QueueSession) delegate;
    return TracingMessageConsumer.create(qs.createReceiver(queue), jmsTracing);
  }

  @Override public QueueReceiver createReceiver(Queue queue, String messageSelector)
    throws JMSException {
    checkQueueSession();
    QueueSession qs = (QueueSession) delegate;
    return TracingMessageConsumer.create(qs.createReceiver(queue, messageSelector), jmsTracing);
  }

  @Override public QueueSender createSender(Queue queue) throws JMSException {
    checkQueueSession();
    QueueSession qs = (QueueSession) delegate;
    return TracingMessageProducer.create(qs.createSender(queue), jmsTracing);
  }

  void checkQueueSession() {
    if ((types & TYPE_QUEUE) != TYPE_QUEUE) {
      throw new IllegalStateException(delegate + " is not a QueueSession");
    }
  }

  // TopicSession

  @Override public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
    checkTopicSession();
    TopicSession ts = (TopicSession) delegate;
    return TracingMessageConsumer.create(ts.createSubscriber(topic), jmsTracing);
  }

  @Override
  public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal)
    throws JMSException {
    checkTopicSession();
    TopicSession ts = (TopicSession) delegate;
    return TracingMessageConsumer.create(ts.createSubscriber(topic, messageSelector, noLocal),
      jmsTracing);
  }

  @Override public TopicPublisher createPublisher(Topic topic) throws JMSException {
    checkTopicSession();
    TopicSession ts = (TopicSession) delegate;
    return TracingMessageProducer.create(ts.createPublisher(topic), jmsTracing);
  }

  void checkTopicSession() {
    if ((types & TYPE_TOPIC) != TYPE_TOPIC) {
      throw new IllegalStateException(delegate + " is not a TopicSession");
    }
  }
}
