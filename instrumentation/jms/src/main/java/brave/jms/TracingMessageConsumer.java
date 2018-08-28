package brave.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.QueueReceiver;
import javax.jms.TopicSubscriber;

class TracingMessageConsumer extends TracingConsumer<MessageConsumer>
    implements MessageConsumer {

  static MessageConsumer create(MessageConsumer delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("messageConsumer == null");
    if (delegate instanceof TracingMessageConsumer) return delegate;
    if (delegate instanceof QueueReceiver) {
      return TracingQueueReceiver.create((QueueReceiver) delegate, jmsTracing);
    }
    if (delegate instanceof TopicSubscriber) {
      return new TracingTopicSubscriber((TopicSubscriber) delegate, jmsTracing);
    }
    return new TracingMessageConsumer(delegate, jmsTracing);
  }

  TracingMessageConsumer(MessageConsumer delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override Destination destination(Message message) {
    try {
      return message.getJMSDestination();
    } catch (JMSException ignored) {
      // don't crash on wonky exceptions!
    }
    return null;
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
}
