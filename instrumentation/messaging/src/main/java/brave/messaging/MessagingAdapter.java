package brave.messaging;

public abstract class MessagingAdapter<Msg> {
  /**
   * Messaging channel, e.g. kafka topic, jms queue, jms topic, etc.
   */
  public abstract String channel(Msg message);

  public abstract String channelTagKey(Msg message);

  /**
   * Messaging operation semantics, e.g. pull, push, send, receive, etc.
   */
  public abstract String operation(Msg message);

  /**
   * Message identifier, e.g. kafka record key, jms message correlation id.
   */
  public abstract String identifier(Msg message);

  /**
   * Messaging broker service, e.g. kafka-cluster, jms-server.
   */
  public abstract String remoteServiceName(Msg message);

  /**
   * Removes propagation context from Message context carrier.
   */
  public abstract void clearPropagation(Msg message);

  public abstract String identifierTagKey();
}
