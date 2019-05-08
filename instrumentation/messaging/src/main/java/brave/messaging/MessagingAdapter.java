package brave.messaging;

public abstract class MessagingAdapter<Msg, Headers> {
    /**
     * Messaging protocol, e.g. kafka, jms, amqp, etc.
     */
    public abstract String protocol(Msg message);

    /**
     * Messaging channel, e.g. kafka topic, jms queue, jms topic, etc.
     */
    public abstract Channel channel(Msg message);

    /**
     * Messaging operation semantics, e.g. pull, push, send, receive, etc.
     */
    public abstract String operation(Msg message);

    /**
     * Messaging broker service, e.g. kafka-cluster, jms-server.
     */
    public abstract String remoteServiceName(Msg message);

    /**
     * Removes propagation context from Message headers (context carrier).
     */
    public abstract void clearPropagation(Headers headers);

    /**
     * Identifies a messaging channel.
     */
    static class Channel {
        final Type type;
        final String name;

        Channel(Type type, String name) {
            this.type = type;
            this.name = name;
        }

        /**
         * Message Channel types: queues and topics.
         */
        enum Type { QUEUE, TOPIC }
    }
}
