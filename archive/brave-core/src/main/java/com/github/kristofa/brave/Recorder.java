package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.InternalSpan;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import javax.annotation.Nullable;
import zipkin.Constants;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.DefaultSpanCodec.toZipkin;

abstract class Recorder implements AnnotationSubmitter.Clock {

  abstract void name(Span span, String name);

  /** Used for local spans spans */
  abstract void start(Span span, long timestamp);

  @Nullable abstract Long timestamp(Span span);

  abstract void annotate(Span span, long timestamp, String value);

  abstract void address(Span span, String key, Endpoint endpoint);

  abstract void tag(Span span, String key, String value);

  /** Implicitly calls flush */
  abstract void finish(Span span, long timestamp);

  /** Reports whatever is present even if unfinished. */
  abstract void flush(Span span);

  @AutoValue
  static abstract class Default extends Recorder {
    abstract Endpoint localEndpoint();

    abstract AnnotationSubmitter.Clock clock();

    abstract Reporter<zipkin.Span> reporter();

    @Override public long currentTimeMicroseconds() {
      return clock().currentTimeMicroseconds();
    }

    @Override void name(Span span, String name) {
      synchronized (span) {
        span.setName(name);
      }
    }

    @Override void start(Span span, long timestamp) {
      synchronized (span) {
        span.setTimestamp(timestamp);
      }
    }

    @Override Long timestamp(Span span) {
      synchronized (span) {
        return span.getTimestamp();
      }
    }

    @Override void annotate(Span span, long timestamp, String value) {
      Annotation annotation = Annotation.create(timestamp, value, localEndpoint());
      synchronized (span) {
        span.addToAnnotations(annotation);
      }
    }

    @Override void address(Span span, String key, Endpoint endpoint) {
      BinaryAnnotation address = BinaryAnnotation.address(key, endpoint);
      synchronized (span) {
        span.addToBinary_annotations(address);
      }
    }

    @Override void tag(Span span, String key, String value) {
      BinaryAnnotation ba = BinaryAnnotation.create(key, value, localEndpoint());
      synchronized (span) {
        span.addToBinary_annotations(ba);
      }
    }

    @Override void finish(Span span, long timestamp) {
      synchronized (span) {
        Long startTimestamp = span.getTimestamp();
        if (startTimestamp != null) {
          span.setDuration(Math.max(1L, timestamp - startTimestamp));
        }
      }
      flush(span);
    }

    @Override void flush(Span span) {
      // In the RPC span model, the client owns the timestamp and duration of the span. If we
      // were propagated an id, we can assume that we shouldn't report timestamp or duration,
      // rather let the client do that. Worst case we were propagated an unreported ID and
      // Zipkin backfills timestamp and duration.
      synchronized (span) {
        if (InternalSpan.instance.context(span).shared) {
          for (int i = 0, length = span.getAnnotations().size(); i < length; i++) {
            if (span.getAnnotations().get(i).value.equals(Constants.SERVER_RECV)) {
              span.setTimestamp(null);
              break;
            }
          }
        }
      }
      reporter().report(toZipkin(span));
    }
  }
}
