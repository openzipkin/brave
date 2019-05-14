package brave.messaging;

public interface ChannelAdapter<Channel> {
  /**
   * Messaging channel, e.g. kafka topic, jms queue, jms topic, etc.
   */
  String channel(Channel channel);

  String channelTagKey(Channel channel);

  /**
   * Messaging broker service, e.g. kafka-cluster, jms-server.
   */
  String remoteServiceName(Channel channel);
}
