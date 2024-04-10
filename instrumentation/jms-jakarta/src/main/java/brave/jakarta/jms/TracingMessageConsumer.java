/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Queue;
import jakarta.jms.QueueReceiver;
import jakarta.jms.QueueSender;
import jakarta.jms.Topic;
import jakarta.jms.TopicPublisher;
import jakarta.jms.TopicSubscriber;

import static brave.jakarta.jms.TracingConnection.TYPE_QUEUE;
import static brave.jakarta.jms.TracingConnection.TYPE_TOPIC;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
final class TracingMessageConsumer extends TracingConsumer<MessageConsumer>
  implements QueueReceiver, TopicSubscriber {

  static TracingMessageConsumer create(MessageConsumer delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingMessageConsumer) return (TracingMessageConsumer) delegate;
    return new TracingMessageConsumer(delegate, jmsTracing);
  }

  final int types;

  TracingMessageConsumer(MessageConsumer delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    int types = 0;
    if (delegate instanceof QueueSender) types |= TYPE_QUEUE;
    if (delegate instanceof TopicPublisher) types |= TYPE_TOPIC;
    this.types = types;
  }

  @Override Destination destination(Message message) {
    return MessageParser.destination(message);
  }

  @Override public String getMessageSelector() throws JMSException {
    return delegate.getMessageSelector();
  }

  @Override public MessageListener getMessageListener() throws JMSException {
    return delegate.getMessageListener();
  }

  @Override public void setMessageListener(MessageListener listener) throws JMSException {
    delegate.setMessageListener(TracingMessageListener.create(listener, jmsTracing));
  }

  @Override public Message receive() throws JMSException {
    Message message = delegate.receive();
    handleReceive(message);
    return message;
  }

  @Override public Message receive(long timeout) throws JMSException {
    Message message = delegate.receive(timeout);
    handleReceive(message);
    return message;
  }

  @Override public Message receiveNoWait() throws JMSException {
    Message message = delegate.receiveNoWait();
    handleReceive(message);
    return message;
  }

  @Override public void close() throws JMSException {
    delegate.close();
  }

  // QueueReceiver

  @Override public Queue getQueue() throws JMSException {
    checkQueueReceiver();
    return ((QueueReceiver) delegate).getQueue();
  }

  void checkQueueReceiver() {
    if ((types & TYPE_QUEUE) != TYPE_QUEUE) {
      throw new IllegalStateException(delegate + " is not a QueueReceiver");
    }
  }

  // TopicSubscriber

  @Override public Topic getTopic() throws JMSException {
    checkTopicSubscriber();
    return ((TopicSubscriber) delegate).getTopic();
  }

  @Override public boolean getNoLocal() throws JMSException {
    checkTopicSubscriber();
    return ((TopicSubscriber) delegate).getNoLocal();
  }

  void checkTopicSubscriber() {
    if ((types & TYPE_TOPIC) != TYPE_TOPIC) {
      throw new IllegalStateException(delegate + " is not a TopicSubscriber");
    }
  }
}
