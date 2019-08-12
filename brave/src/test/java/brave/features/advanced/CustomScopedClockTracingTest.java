/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.features.advanced;

import brave.Clock;
import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One advanced use case is speculatively starting a span for a connection, but only sending that
 * span if a query occurred on it. By default, spans have a clock pinned to the trace. To use a
 * clock pinned to a connection, you have to control timestamps manually.
 *
 * <p>See https://github.com/apache/incubator-zipkin-brave/issues/564
 */
public class CustomScopedClockTracingTest {
  List<Span> spans = new ArrayList<>();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(ThreadLocalCurrentTraceContext.create())
    .spanReporter(spans::add)
    .build();

  @After public void close() {
    Tracing.current().close();
  }

  class Connection {
    final UUID id;
    boolean used;

    Connection() {
      id = UUID.randomUUID();
    }

    void reserve() {
      used = true;
    }

    void release() {
    }

    void destroy() {
    }
  }

  class Query {
    final Connection connection;

    Query(Connection connection) {
      this.connection = connection;
    }

    void execute() {
      connection.reserve();
      // pretend you do something
      connection.release();
    }
  }

  /**
   * Here, we speculatively start a trace based on a connection, which is abandoned of there are no
   * queries. As each query will be in a separate trace, we need to manually control timestamps.
   * This allows alignment of timestamps at microsecond granularity, but still expire when the
   * connection does.
   */
  @Test public void customClock() {
    class TracedConnection extends Connection {
      final brave.Span span;
      final brave.Clock clock;

      TracedConnection() {
        span = tracing.tracer().nextSpan().name("connection").start()
          .tag("connection.id", id.toString());
        clock = tracing.clock(span.context());
      }

      @Override void destroy() {
        if (!used) {
          span.abandon();
        } else {
          span.finish();
        }
      }
    }

    class TracedQuery extends Query {
      final Clock clock;

      TracedQuery(TracedConnection connection) {
        super(connection);
        clock = connection.clock;
      }

      @Override void execute() {
        // notice we are using the clock from the connection, which means eventhough
        // this is a different trace, the timestamps will be aligned.
        brave.Span span = tracing.tracer().nextSpan().name("query")
          .tag("connection.id", connection.id.toString())
          .start(clock.currentTimeMicroseconds());
        super.execute();
        span.finish(clock.currentTimeMicroseconds());
      }
    }

    // pretend we had someone allocate a connection, and a query, but not use it
    TracedConnection connection = new TracedConnection();
    new TracedQuery(connection);
    connection.destroy();

    // our intent was to not record any spans as a result
    assertThat(spans).isEmpty();

    // However, if we have a query executed, we want to record the lifecycle of the connection
    // in another trace.
    TracedConnection connection2 = new TracedConnection();
    // two queries
    new TracedQuery(connection2).execute();
    new TracedQuery(connection2).execute();
    connection2.destroy();

    // we expect a trace for each query and one for the connection
    assertThat(spans)
      .hasSize(3)
      // we expect to be able to correlate all traces by the connection ID
      .allSatisfy(s -> assertThat(s.tags())
        .containsEntry("connection.id", connection2.id.toString()));
  }
}
