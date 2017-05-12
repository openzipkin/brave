package brave.cassandra;

import brave.Tracer;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import java.nio.ByteBuffer;
import java.util.Map;
import zipkin.internal.Util;

abstract class TracingComponent {
  /** Getter that pulls trace fields from ascii values */
  static final Propagation.Getter<Map<String, ByteBuffer>, String> GETTER = (carrier, key) -> {
    ByteBuffer buf = carrier.get(key);
    return buf != null ? Util.UTF_8.decode(buf).toString() : null;
  };

  abstract Tracer tracer();

  abstract TraceContext.Extractor<Map<String, ByteBuffer>> extractor();

  static final class Current extends TracingComponent {
    @Override Tracer tracer() {
      return brave.Tracing.currentTracer();
    }

    @Override TraceContext.Extractor<Map<String, ByteBuffer>> extractor() {
      brave.Tracing tracing = brave.Tracing.current();
      return tracing != null ? tracing.propagation().extractor(GETTER) : null;
    }
  }

  static final class Explicit extends TracingComponent {
    final Tracer tracer;
    final TraceContext.Extractor<Map<String, ByteBuffer>> extractor;

    Explicit(brave.Tracing tracing) {
      if (tracing == null) throw new NullPointerException("tracing == null");
      this.tracer = tracing.tracer();
      this.extractor = tracing.propagation().extractor(GETTER);
    }

    @Override Tracer tracer() {
      return tracer;
    }

    @Override TraceContext.Extractor<Map<String, ByteBuffer>> extractor() {
      return extractor;
    }
  }
}
