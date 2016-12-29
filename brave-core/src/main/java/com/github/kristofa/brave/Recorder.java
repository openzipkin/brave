package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.reporter.Reporter;

import static com.github.kristofa.brave.internal.DefaultSpanCodec.toZipkin;

abstract class Recorder {
  abstract void name(Span span, String name);

  abstract void start(Span span, long timestamp);

  abstract void annotate(Span span, long timestamp, String value);

  abstract void address(Span span, String key, Endpoint endpoint);

  abstract void tag(Span span, String key, String value);

  /** Implicitly calls flush */
  abstract void finishWithTimestamp(Span span, long endTimestamp);

  /** Implicitly calls flush */
  abstract void finishWithDuration(Span span, long duration);

  /** Reports whatever is present even if unfinished. */
  abstract void flush(Span span);

  @AutoValue
  static abstract class Default extends Recorder {
    abstract Endpoint localEndpoint();

    abstract Reporter<zipkin.Span> reporter();

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

    @Override void annotate(Span span, long timestamp, String value) {
      Annotation annotation = Annotation.create(timestamp, value, localEndpoint());
      synchronized (span) {
        span.addToAnnotations(annotation);
      }
    }

    @Override void address(Span span, String key, Endpoint endpoint) {
      BinaryAnnotation ba = BinaryAnnotation.address(key, endpoint);
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

    @Override void finishWithTimestamp(Span span, long endTimestamp) {
      synchronized (span) {
        Long startTimestamp = span.getTimestamp();
        if (startTimestamp != null) {
          span.setDuration(Math.max(1L, endTimestamp - startTimestamp));
        }
      }
      flush(span);
    }

    @Override void finishWithDuration(Span span, long duration) {
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
