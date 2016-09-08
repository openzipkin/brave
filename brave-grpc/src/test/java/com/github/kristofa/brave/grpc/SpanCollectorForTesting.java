package com.github.kristofa.brave.grpc;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

enum SpanCollectorForTesting implements SpanCollector {
  INSTANCE;

  private final static Logger LOGGER = Logger.getLogger(SpanCollectorForTesting.class.getName());

  private final ConcurrentLinkedQueue<Span> spans = new ConcurrentLinkedQueue<Span>();

  @Override public void collect(Span span) {
    LOGGER.info(span.toString());
    spans.add(span);
  }

  List<Span> getCollectedSpans() {
    return new ArrayList<>(spans);
  }

  void clear() {
    spans.clear();
  }

  @Override
  public void addDefaultAnnotation(final String key, final String value) {
    throw new UnsupportedOperationException();
  }
}
