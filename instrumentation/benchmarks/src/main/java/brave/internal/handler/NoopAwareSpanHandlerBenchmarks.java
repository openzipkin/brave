/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.internal.handler;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.handler.SpanHandler.Cause;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static java.util.Arrays.asList;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class NoopAwareSpanHandlerBenchmarks {
  AtomicBoolean noop = new AtomicBoolean();
  SpanHandler one = new SpanHandler() {
    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      span.tag("one", "");
      return true;
    }

    @Override public String toString() {
      return "one";
    }
  };
  SpanHandler two = new SpanHandler() {
    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      span.tag("two", "");
      return true;
    }

    @Override public String toString() {
      return "two";
    }
  };
  SpanHandler three = new SpanHandler() {
    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      span.tag("three", "");
      return true;
    }

    @Override public String toString() {
      return "three";
    }
  };

  final SpanHandler composite =
    NoopAwareSpanHandler.create(new SpanHandler[] {one, two, three}, noop);
  final SpanHandler listIndexComposite = new SpanHandler() {

    List<SpanHandler> delegates = asList(one, two, three);

    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      for (int i = 0, length = delegates.size(); i < length; i++) {
        if (!delegates.get(i).end(context, span, cause)) return false;
      }
      return true;
    }
  };
  final SpanHandler listIteratorComposite = new SpanHandler() {
    List<SpanHandler> delegates = asList(one, two, three);

    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      for (SpanHandler delegate : delegates) {
        if (!delegate.end(context, span, cause)) return false;
      }
      return true;
    }
  };
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();

  @Benchmark public void compose() {
    composite.end(context, new MutableSpan(), Cause.FINISH);
  }

  @Benchmark public void compose_index() {
    listIndexComposite.end(context, new MutableSpan(), Cause.FINISH);
  }

  @Benchmark public void compose_iterator() {
    listIteratorComposite.end(context, new MutableSpan(), Cause.FINISH);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + NoopAwareSpanHandlerBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
