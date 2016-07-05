package com.github.kristofa.brave.cxf3;

import java.util.ArrayList;
import java.util.List;
import zipkin.Span;
import zipkin.reporter.Reporter;

public class ReporterForTesting implements Reporter<Span> {

  private List<Span> spans = new ArrayList<>();

  @Override
  public void report(Span span) {
    spans.add(span);
  }

  public List<Span> getCollectedSpans() {
    return spans;
  }
}