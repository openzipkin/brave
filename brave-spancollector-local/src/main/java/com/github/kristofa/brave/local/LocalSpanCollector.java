package com.github.kristofa.brave.local;

import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.FlushingSpanCollector;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;
import zipkin.storage.StorageComponent;

import static com.github.kristofa.brave.internal.DefaultSpanCodec.toZipkin;

/**
 * SpanCollector which submits spans directly to a Zipkin {@link StorageComponent}.
 *
 * @deprecated replaced by {@link zipkin.reporter.AsyncReporter} and {@code LocalSender}
 *             located in the "io.zipkin.reporter:zipkin-sender-local" dependency.
 */
@Deprecated
public final class LocalSpanCollector extends FlushingSpanCollector {

  @AutoValue
  public static abstract class Config {
    public static Builder builder() {
      return new AutoValue_LocalSpanCollector_Config.Builder()
          .flushInterval(1);
    }

    abstract int flushInterval();

    @AutoValue.Builder
    public interface Builder {
      /** Default 1 second. 0 implies spans are {@link #flush() flushed} externally. */
      Builder flushInterval(int flushInterval);

      Config build();
    }
  }

  private final StorageComponent storageComponent;
  private final SpanCollectorMetricsHandler metrics;

  /**
   * Create a new instance with default configuration.
   *
   * @param storageComponent spans will be written asynchronously to this
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   *                these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static LocalSpanCollector create(StorageComponent storageComponent,
      SpanCollectorMetricsHandler metrics) {
    return new LocalSpanCollector(storageComponent, Config.builder().build(), metrics);
  }

  /**
   * @param storageComponent spans will be written asynchronously to this
   * @param config includes flush interval and timeouts
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   *                these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static LocalSpanCollector create(StorageComponent storageComponent, Config config,
      SpanCollectorMetricsHandler metrics) {
    return new LocalSpanCollector(storageComponent, config, metrics);
  }

  // Visible for testing. Ex when tests need to explicitly control flushing, set interval to 0.
  LocalSpanCollector(StorageComponent storageComponent, Config config,
      SpanCollectorMetricsHandler metrics) {
    super(metrics, config.flushInterval());
    this.storageComponent = storageComponent;
    this.metrics = metrics;
  }

  @Override protected void reportSpans(final List<Span> drained) throws IOException {
    // Brave 3 doesn't use zipkin spans. Convert accordingly
    List<zipkin.Span> zipkinSpans = new ArrayList<zipkin.Span>(drained.size());
    for (Span input : drained) {
      zipkinSpans.add(toZipkin(input));
    }
    // This dereferences a lazy, which might throw an exception if the storage system is down.
    AsyncSpanConsumer asyncSpanConsumer = storageComponent.asyncSpanConsumer();

    // We accept the spans into storage, incrementing drop metrics if there was a failure.
    asyncSpanConsumer.accept(zipkinSpans, new Callback<Void>() {
      @Override public void onSuccess(Void ignored) {
      }

      @Override public void onError(Throwable throwable) {
        metrics.incrementDroppedSpans(drained.size());
      }
    });
  }
}
