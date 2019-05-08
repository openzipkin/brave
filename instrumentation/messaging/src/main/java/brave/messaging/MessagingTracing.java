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
  final MessagingParser parser;

  MessagingTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.parser = builder.parser;
  }

  public Tracing tracing() {
    return tracing;
  }

  public static class Builder {
    final Tracing tracing;
    MessagingParser parser = new MessagingParser();

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
    }

    public Builder parser(MessagingParser parser) {
      if (parser == null) throw new NullPointerException("parser == null");
      this.parser = parser;
      return this;
    }

    MessagingTracing build() {
      return new MessagingTracing(this);
    }
  }
}
