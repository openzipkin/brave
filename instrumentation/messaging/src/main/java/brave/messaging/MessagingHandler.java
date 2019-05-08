package brave.messaging;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

abstract class MessagingHandler<Msg, A extends MessagingAdapter<Msg>> {

  final CurrentTraceContext currentTraceContext;
  final A adapter;
  final MessagingParser parser;
  final TraceContext.Extractor<Msg> extractor;
  final TraceContext.Injector<Msg> injector;

  MessagingHandler(CurrentTraceContext currentTraceContext,
      A adapter,
      MessagingParser parser,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    this.currentTraceContext = currentTraceContext;
    this.adapter = adapter;
    this.parser = parser;
    this.extractor = extractor;
    this.injector = injector;
  }
}
