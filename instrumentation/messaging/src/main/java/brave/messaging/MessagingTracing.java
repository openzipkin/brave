package brave.messaging;

import brave.Tracing;

public class MessagingTracing {

  public static MessagingTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static MessagingTracing.Builder newBuilder(Tracing tracing) {
    return new MessagingTracing.Builder(tracing);
  }

  final Tracing tracing;
  final MessagingConsumerParser consumerParser;
  final MessagingProducerParser producerParser;

  MessagingTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.consumerParser = builder.consumerParser;
    this.producerParser = builder.producerParser;
  }

  public Tracing tracing() {
    return tracing;
  }

  public MessagingProducerParser producerParser() {
    return producerParser;
  }

  public MessagingConsumerParser consumerParser() {
    return consumerParser;
  }

  public static class Builder {
    final Tracing tracing;
    MessagingConsumerParser consumerParser = new MessagingConsumerParser();
    MessagingProducerParser producerParser = new MessagingProducerParser();

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public Builder consumerParser(MessagingConsumerParser consumerParser) {
      if (producerParser == null) throw new NullPointerException("consumerParser == null");
      this.consumerParser = consumerParser;
      return this;
    }

    public Builder producerParser(MessagingProducerParser producerParser) {
      if (producerParser == null) throw new NullPointerException("producerParser == null");
      this.producerParser = producerParser;
      return this;
    }

    MessagingTracing build() {
      return new MessagingTracing(this);
    }
  }
}
