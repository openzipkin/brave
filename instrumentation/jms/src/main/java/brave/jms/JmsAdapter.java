package brave.jms;

import brave.messaging.ChannelAdapter;
import brave.messaging.MessageAdapter;
import javax.jms.Destination;
import javax.jms.Message;

class JmsAdapter {

  //TODO
  static class JmsMessageAdapter implements MessageAdapter<Message> {

    final JmsTracing jmsTracing;

    JmsMessageAdapter(JmsTracing jmsTracing) {
      this.jmsTracing = jmsTracing;
    }

    static JmsMessageAdapter create(JmsTracing jmsTracing) {
      return new JmsMessageAdapter(jmsTracing);
    }

    @Override public String operation(Message message) {
      return null;
    }

    @Override public String identifier(Message message) {
      return null;
    }

    @Override public void clearPropagation(Message message) {

    }

    @Override public String identifierTagKey() {
      return null;
    }
  }

  //TODO
  static class JmsChannelAdapter implements ChannelAdapter<Destination> {

    final JmsTracing jmsTracing;

    JmsChannelAdapter(JmsTracing jmsTracing) {
      this.jmsTracing = jmsTracing;
    }

    static JmsChannelAdapter create(JmsTracing jmsTracing) {
      return new JmsChannelAdapter(jmsTracing);
    }

    @Override public String channel(Destination message) {
      return null;
    }

    @Override public String channelTagKey(Destination message) {
      return null;
    }

    @Override public String remoteServiceName(Destination message) {
      return null;
    }
  }
}
