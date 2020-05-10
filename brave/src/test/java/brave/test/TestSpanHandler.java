package brave.test;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

// copy of what's in brave-tests
public final class TestSpanHandler extends SpanHandler implements Iterable<MutableSpan> {
  final List<MutableSpan> spans = new ArrayList<>();

  public MutableSpan get(int i) {
    return spans.get(i);
  }

  public List<MutableSpan> spans() {
    return spans;
  }

  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    if (cause == Cause.ABANDONED) return true;
    spans.add(span);
    return true;
  }

  public Stream<MutableSpan> stream() {
    return spans.stream();
  }

  @Override public Iterator<MutableSpan> iterator() {
    return spans.iterator();
  }

  public void clear() {
    spans.clear();
  }

  @Override public String toString() {
    return "TestSpanHandler{" + spans + "}";
  }
}
