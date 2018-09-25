/*
 * Copyright 2015-2018 The OpenZipkin Authors
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

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
public class FinishedSpanHandlersBenchmarks {
  FinishedSpanHandler one = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      span.tag("one", "");
      return true;
    }

    @Override public String toString() {
      return "one";
    }
  };
  FinishedSpanHandler two = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      span.tag("two", "");
      return true;
    }

    @Override public String toString() {
      return "two";
    }
  };
  FinishedSpanHandler three = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      span.tag("three", "");
      return true;
    }

    @Override public String toString() {
      return "three";
    }
  };

  final FinishedSpanHandler composite = FinishedSpanHandlers.compose(asList(one, two, three));
  final FinishedSpanHandler listIndexComposite = new FinishedSpanHandler() {
    List<FinishedSpanHandler> delegates = asList(one, two, three);

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      for (int i = 0, length = delegates.size(); i < length; i++) {
        if (!delegates.get(i).handle(context, span)) return false;
      }
      return true;
    }
  };
  final FinishedSpanHandler listIteratorComposite = new FinishedSpanHandler() {
    List<FinishedSpanHandler> delegates = asList(one, two, three);

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      for (FinishedSpanHandler delegate : delegates) {
        if (!delegate.handle(context, span)) return false;
      }
      return true;
    }
  };
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(true).build();

  @Benchmark public void compose() {
    composite.handle(context, new MutableSpan());
  }

  @Benchmark public void compose_index() {
    listIndexComposite.handle(context, new MutableSpan());
  }

  @Benchmark public void compose_iterator() {
    listIteratorComposite.handle(context, new MutableSpan());
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + FinishedSpanHandlersBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
