package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.InternalSpan;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.Constants;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.DefaultSpanCodec.toZipkin;

abstract class Recorder {
  enum SpanKind {
    CLIENT,
    SERVER
  }

  abstract void name(Span span, String name);

  abstract void start(Span span);

  abstract void start(Span span, SpanKind kind);

  abstract void start(Span span, long timestamp);

  abstract void annotate(Span span, String value);

  abstract void annotate(Span span, long timestamp, String value);

  abstract void remoteAddress(Span span, SpanKind kind, Endpoint endpoint);

  abstract void tag(Span span, String key, String value);

  /** Implicitly calls flush */
  abstract void finish(Span span);

  /** Implicitly calls flush */
  abstract void finish(Span span, SpanKind kind);

  /** Implicitly calls flush */
  abstract void finish(Span span, long duration);

  /** Reports whatever is present even if unfinished. */
  abstract void flush(Span span);

  @AutoValue
  static abstract class Default extends Recorder {
    abstract Endpoint localEndpoint();

    abstract AnnotationSubmitter.Clock clock();

    abstract Reporter<zipkin.Span> reporter();

    @Override void name(Span span, String name) {
      synchronized (span) {
        span.setName(name);
      }
    }

    @Override void start(Span span) {
      start(span, clock().currentTimeMicroseconds());
    }

    @Override void start(Span span, SpanKind kind) {
      String value;
      switch (kind) {
        case CLIENT:
          value = Constants.CLIENT_SEND;
          break;
        case SERVER:
          value = Constants.SERVER_RECV;
          break;
        default:
          throw new AssertionError(kind + " is not yet supported");
      }
      Long timestamp = clock().currentTimeMicroseconds();
      Annotation annotation = Annotation.create(timestamp, value, localEndpoint());

      // In the RPC span model, the client owns the timestamp and duration of the span. If we
      // were propagated an id, we can assume that we shouldn't report timestamp or duration,
      // rather let the client do that. Worst case we were propagated an unreported ID and
      // Zipkin backfills timestamp and duration.
      boolean serverHalf = InternalSpan.instance.context(span).shared && kind == SpanKind.SERVER;
      if (serverHalf) timestamp = null;

      synchronized (span) {
        span.setTimestamp(timestamp);
        span.addToAnnotations(annotation);
      }
    }

    @Override void start(Span span, long timestamp) {
      synchronized (span) {
        span.setTimestamp(timestamp);
      }
    }

    @Override void annotate(Span span, String value) {
      annotate(span, clock().currentTimeMicroseconds(), value);
    }

    @Override void annotate(Span span, long timestamp, String value) {
      Annotation annotation = Annotation.create(timestamp, value, localEndpoint());
      synchronized (span) {
        span.addToAnnotations(annotation);
      }
    }

    @Override void remoteAddress(Span span, SpanKind kind, Endpoint endpoint) {
      String address;
      switch (kind) {
        case CLIENT:
          address = Constants.SERVER_ADDR;
          break;
        case SERVER:
          address = Constants.CLIENT_ADDR;
          break;
        default:
          throw new AssertionError(kind + " is not yet supported");
      }
      BinaryAnnotation ba = BinaryAnnotation.address(address, endpoint);
      synchronized (span) {
        span.addToBinary_annotations(ba);
      }
    }

    @Override void tag(Span span, String key, String value) {
      BinaryAnnotation ba = BinaryAnnotation.create(key, value, localEndpoint());
      synchronized (span) {
        span.addToBinary_annotations(ba);
      }
    }

    @Override void finish(Span span) {
      long endTimestamp = clock().currentTimeMicroseconds();
      synchronized (span) {
        Long startTimestamp = span.getTimestamp();
        if (startTimestamp != null) {
          span.setDuration(Math.max(1L, endTimestamp - startTimestamp));
        }
      }
      flush(span);
    }

    @Override void finish(Span span, SpanKind kind) {
      String value;
      switch (kind) {
        case CLIENT:
          value = Constants.CLIENT_RECV;
          break;
        case SERVER:
          value = Constants.SERVER_SEND;
          break;
        default:
          throw new AssertionError(kind + " is not yet supported");
      }
      long endTimestamp = clock().currentTimeMicroseconds();
      Annotation annotation = Annotation.create(endTimestamp, value, localEndpoint());

      synchronized (span) {
        span.addToAnnotations(annotation);
        Long startTimestamp = span.getTimestamp();
        if (startTimestamp != null) {
          span.setDuration(Math.max(1L, endTimestamp - startTimestamp));
        }
      }
      flush(span);
    }

    @Override void finish(Span span, long duration) {
      synchronized (span) {
        span.setDuration(duration);
      }
      flush(span);
    }

    @Override void flush(Span span) {
      reporter().report(toZipkin(span));
    }
  }
}
