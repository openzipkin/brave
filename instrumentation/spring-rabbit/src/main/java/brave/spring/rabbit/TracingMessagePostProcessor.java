package brave.spring.rabbit;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * MessagePostProcessor to be used with the {@link RabbitTemplate#setBeforePublishPostProcessors
 * RabbitTemplate's before publish post processors}, adding tracing functionality that creates a
 * {@link Span.Kind#PRODUCER} span.
 */
final class TracingMessagePostProcessor implements MessagePostProcessor {

  final SpringRabbitTracing springRabbitTracing;
  final Tracing tracing;
  final Tracer tracer;
  final CurrentTraceContext currentTraceContext;
  final Injector<MessageProperties> injector;
  @Nullable final String remoteServiceName;

  TracingMessagePostProcessor(SpringRabbitTracing springRabbitTracing) {
    this.springRabbitTracing = springRabbitTracing;
    this.tracing = springRabbitTracing.tracing;
    this.tracer = tracing.tracer();
    this.currentTraceContext = tracing.currentTraceContext();
    this.injector = springRabbitTracing.injector;
    this.remoteServiceName = springRabbitTracing.remoteServiceName;
  }

  @Override public Message postProcessMessage(Message message) {
    TraceContext maybeParent = currentTraceContext.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      span = tracer.nextSpan(springRabbitTracing.extractAndClearHeaders(message));
    } else {
      // If we have a span in scope assume headers were cleared before
      span = tracer.newChild(maybeParent);
    }

    if (!span.isNoop()) {
      span.kind(Span.Kind.PRODUCER).name("publish");
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      // incur timestamp overhead only once
      long timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
      span.start(timestamp).finish(timestamp);
    }

    injector.inject(span.context(), message.getMessageProperties());
    return message;
  }
}
