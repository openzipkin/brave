package brave.jms;

import brave.messaging.ChannelAdapter;
import brave.messaging.MessageAdapter;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Topic;

import static brave.jms.JmsTracing.JMS_QUEUE;
import static brave.jms.JmsTracing.JMS_TOPIC;

class JmsAdapter {

  static class JmsConsumerMessageAdapter implements MessageAdapter<Message> {

    final JmsTracing jmsTracing;

    JmsConsumerMessageAdapter(JmsTracing jmsTracing) {
      this.jmsTracing = jmsTracing;
    }

    static JmsConsumerMessageAdapter create(JmsTracing jmsTracing) {
      return new JmsConsumerMessageAdapter(jmsTracing);
    }

    @Override public String operation(Message message) {
      return "receive";
    }

    @Override public String identifier(Message message) {
      try {
        return message.getJMSCorrelationID();
      } catch (JMSException e) {
        // don't crash on wonky exceptions!
      }
      return null;
    }

    @Override public void clearPropagation(Message message) {
      PropertyFilter.JMS_PRODUCER.filterProperties(message, jmsTracing.propagationKeys);
    }

    @Override public String identifierTagKey() {
      return "jms.correlation_id";
    }
  }

  static class JmsProducerMessageAdapter implements MessageAdapter<Message> {

    final JmsTracing jmsTracing;

    JmsProducerMessageAdapter(JmsTracing jmsTracing) {
      this.jmsTracing = jmsTracing;
    }

    static JmsProducerMessageAdapter create(JmsTracing jmsTracing) {
      return new JmsProducerMessageAdapter(jmsTracing);
    }

    @Override public String operation(Message message) {
      return "send";
    }

    @Override public String identifier(Message message) {
      try {
        return message.getJMSCorrelationID();
      } catch (JMSException e) {
        // don't crash on wonky exceptions!
      }
      return null;
    }

    @Override public void clearPropagation(Message message) {
      PropertyFilter.JMS_PRODUCER.filterProperties(message, jmsTracing.propagationKeys);
    }

    @Override public String identifierTagKey() {
      return null;
    }
  }

  static class JmsChannelAdapter implements ChannelAdapter<Destination> {

    final JmsTracing jmsTracing;

    JmsChannelAdapter(JmsTracing jmsTracing) {
      this.jmsTracing = jmsTracing;
    }

    static JmsChannelAdapter create(JmsTracing jmsTracing) {
      return new JmsChannelAdapter(jmsTracing);
    }

    @Override public String channel(Destination destination) {
      try {
        if (destination instanceof Queue) {
          return ((Queue) destination).getQueueName();
        } else if (destination instanceof Topic) {
          return ((Topic) destination).getTopicName();
        }
      } catch (JMSException ignored) {
        // don't crash on wonky exceptions!
      }
      return null;
    }

    @Override public String channelTagKey(Destination destination) {
      if (destination instanceof Queue) {
        return JMS_QUEUE;
      } else if (destination instanceof Topic) {
        return JMS_TOPIC;
      }
      return null;
    }

    @Override public String remoteServiceName(Destination message) {
      return jmsTracing.remoteServiceName;
    }
  }
}
