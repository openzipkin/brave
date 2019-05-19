package brave.messaging;

public interface MessageConsumerAdapter<Msg> extends MessageAdapter<Msg> {
  /**
   * Messaging operation semantics, e.g. pull, push, send, receive, etc.
   */
  String operation(Msg message);

  /**
   * Message identifier, e.g. kafka record key, jms message correlation id.
   */
  String identifier(Msg message);

  /**
   * Removes propagation context from Message context carrier.
   */
  void clearPropagation(Msg message);

  String identifierTagKey();
}
