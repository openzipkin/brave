/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.messaging;

import brave.Span;
import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.internal.Nullable;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Instances built via {@link #create(Tracing)} or {@link #newBuilder(Tracing)} are registered
 * automatically such that statically configured instrumentation like HTTP producers can use {@link
 * #current()}.
 *
 * @since 5.9
 */
public class MessagingTracing implements Closeable {
  static final AtomicReference<MessagingTracing> CURRENT = new AtomicReference<>();

  /** @since 5.9 */
  public static MessagingTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  /** @since 5.9 */
  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  /** @since 5.9 */
  public Tracing tracing() {
    return tracing;
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link SamplerFunctions#deferDecision() trace ID instead}.
   *
   * <p>This decision happens when a trace was not yet started in process. For example, you may be
   * making an messaging request as a part of booting your application. You may want to opt-out of
   * tracing producer requests that did not originate from a consumer request.
   *
   * @see SamplerFunctions
   * @see MessagingRuleSampler
   * @since 5.9
   */
  public SamplerFunction<MessagingRequest> producerSampler() {
    return producerSampler;
  }

  /**
   * Returns an overriding sampling decision for a new trace. Defaults to ignore the request and use
   * the {@link SamplerFunctions#deferDecision() trace ID instead}.
   *
   * <p>This decision happens when trace IDs were not in headers, or a sampling decision has not
   * yet been made. For example, if a trace is already in progress, this function is not called. You
   * can implement this to skip channels that you never want to trace.
   *
   * @see SamplerFunctions
   * @see MessagingRuleSampler
   * @since 5.9
   */
  public SamplerFunction<MessagingRequest> consumerSampler() {
    return consumerSampler;
  }

  /**
   * Returns the propagation component used by messaging instrumentation.
   *
   * <p>Typically, this is the same as {@link Tracing#propagation()}. Overrides will apply to all
   * messaging instrumentation in use. For example, Kafka and also AMQP. If only trying to change B3
   * related headers, use the more efficient {@link B3Propagation.FactoryBuilder#injectFormat(Span.Kind,
   * B3Propagation.Format)} instead.
   *
   * <h3>Use caution when overriding</h3>
   * If overriding this via {@link Builder#propagation(Propagation)}, take care to also delegate to
   * {@link Tracing#propagation()}. Otherwise, you can break features something else may have set,
   * such as {@link BaggagePropagation}.
   *
   * <h3>Library-specific formats</h3>
   * Messaging instrumentation can localize propagation changes by calling {@link #toBuilder()},
   * then {@link Builder#propagation(Propagation)}. This allows library-specific formats.
   *
   * <h4>Example 1: Apache Camel</h4>
   * Apache Camel has multiple trace instrumentation. Instead of using a single header like "b3" to
   * avoid JMS propagation problems, their OpenTracing implementation replaces hyphens with
   * <a href="https://github.com/apache/camel/blob/992a6b9685f4db49236e540af2546548cf99a7d3/components/camel-tracing/src/main/java/org/apache/camel/tracing/propagation/CamelMessagingHeadersInjectAdapter.java">"_$dash$_"</a>.
   *
   * <p>For example, this would cause "X-B3-TraceId" to become "X_$dash$_B3_$dash$_TraceId". When
   * normal instrumentation is on the other side, it would not guess to use this convention, and
   * fail to resume the opentracing created trace.
   *
   * <p>A custom propagation component used only for camel could tolerantly read this replacement
   * format and write down the safe and more efficient {@link B3Propagation.Format#SINGLE_NO_PARENT}
   * instead. It could do this with configuration, such as "opentracing compatibility" and not add
   * the same overhead to other libraries.
   *
   * <h4>Example 2: Spring Messaging</h4>
   * Spring Messaging is used for both in-memory and remote channels. Transports such as STOMP may
   * require speculatively duplicating propagation fields as <a href="https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/messaging/support/NativeMessageHeaderAccessor.html#NATIVE_HEADERS">Native
   * Headers</a>.
   *
   * <p>You could make a custom {@link Propagation.RemoteSetter} that takes into consideration if
   * a message has native support or not, and add "native headers" only when needed by the message
   * in use instead of adding headers to all messaging libraries.
   *
   * <pre>{@code
   * SimpMessageHeaderAccessor accessor =
   *     MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
   *
   * if (accessor != null) {
   *   // Add native headers..
   * }
   * }</pre>
   *
   * @see Tracing#propagation()
   * @since 5.13
   */
  public Propagation<String> propagation() {
    return propagation;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  final Tracing tracing;
  final SamplerFunction<MessagingRequest> producerSampler;
  final SamplerFunction<MessagingRequest> consumerSampler;
  final Propagation<String> propagation;

  MessagingTracing(Builder builder) {
    this.tracing = builder.tracing;
    this.producerSampler = builder.producerSampler;
    this.consumerSampler = builder.consumerSampler;
    this.propagation = builder.propagation;
    // assign current IFF there's no instance already current
    CURRENT.compareAndSet(null, this);
  }

  public static final class Builder {
    Tracing tracing;
    SamplerFunction<MessagingRequest> producerSampler;
    SamplerFunction<MessagingRequest> consumerSampler;
    Propagation<String> propagation;

    Builder(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      this.propagation = tracing.propagation();
      this.producerSampler = SamplerFunctions.deferDecision();
      this.consumerSampler = SamplerFunctions.deferDecision();
    }

    Builder(MessagingTracing source) {
      this.tracing = source.tracing;
      this.producerSampler = source.producerSampler;
      this.consumerSampler = source.consumerSampler;
      this.propagation = source.propagation;
    }

    /** @see MessagingTracing#tracing() */
    public Builder tracing(Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracing = tracing;
      return this;
    }

    /** @see MessagingTracing#producerSampler() */
    public Builder producerSampler(SamplerFunction<MessagingRequest> producerSampler) {
      if (producerSampler == null) throw new NullPointerException("producerSampler == null");
      this.producerSampler = producerSampler;
      return this;
    }

    /** @see MessagingTracing#consumerSampler() */
    public Builder consumerSampler(SamplerFunction<MessagingRequest> consumerSampler) {
      if (consumerSampler == null) throw new NullPointerException("consumerSampler == null");
      this.consumerSampler = consumerSampler;
      return this;
    }

    /** @see MessagingTracing#propagation() */
    public Builder propagation(Propagation<String> propagation) {
      if (propagation == null) throw new NullPointerException("propagation == null");
      this.propagation = propagation;
      return this;
    }

    public MessagingTracing build() {
      return new MessagingTracing(this);
    }
  }

  /**
   * Returns the most recently created tracing component iff it hasn't been closed. null otherwise.
   *
   * <p>This object should not be cached.
   *
   * @since 5.9
   */
  @Nullable public static MessagingTracing current() {
    return CURRENT.get();
  }

  /** @since 5.9 */
  @Override public void close() {
    // only set null if we are the outer-most instance
    CURRENT.compareAndSet(this, null);
  }
}
