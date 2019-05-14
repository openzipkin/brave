package brave.messaging;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

abstract class MessagingHandler<Chan, Msg, CA extends ChannelAdapter<Chan>, MA extends MessageAdapter<Msg>> {

  final CurrentTraceContext currentTraceContext;
  final CA channelAdapter;
  final MA messageAdapter;
  final MessagingParser parser;
  final TraceContext.Extractor<Msg> extractor;
  final TraceContext.Injector<Msg> injector;

  MessagingHandler(
      CurrentTraceContext currentTraceContext,
      CA channelAdapter,
      MA adapter,
      MessagingParser parser,
      TraceContext.Extractor<Msg> extractor,
      TraceContext.Injector<Msg> injector) {
    this.currentTraceContext = currentTraceContext;
    this.channelAdapter = channelAdapter;
    this.messageAdapter = adapter;
    this.parser = parser;
    this.extractor = extractor;
    this.injector = injector;
  }
}
