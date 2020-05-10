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
package brave.features.handler;

import brave.ScopedSpan;
import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.B3SingleFormat;
import brave.propagation.TraceContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This shows how you can skip local spans to reduce the cost of storage. In this example we are
 * cherry-picking data used by the dependency linker, mostly to make it simpler.
 */
public class SkeletalSpansTest {
  static class RetainSkeletalSpans extends SpanHandler {
    @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      if (cause == Cause.ABANDONED || span.kind() == null) return false; // skip local spans

      if (!context.isLocalRoot()) {
        span.parentId(context.localRootIdString()); // rewrite the parent ID
      }
      span.forEachAnnotation((timestamp, value) -> null); // drop all annotations
      span.forEachTag((key, value) -> {
        if (key.equals("error")) return ""; // linking counts errors: the value isn't important
        return null;
      });
      span.remoteIp(null); // retain only the service name
      span.remotePort(0);
      return true;
    }
  }

  Map<String, List<MutableSpan>>
    spans = new LinkedHashMap<>(),
    skeletalSpans = new LinkedHashMap<>();

  Tracer server1Tracer = Tracing.newBuilder()
    .localServiceName("server1")
    .addSpanHandler(toSpanHandler(spans))
    .build().tracer();

  Tracer server2Tracer = Tracing.newBuilder()
    .localServiceName("server2")
    .addSpanHandler(toSpanHandler(spans))
    .build().tracer();

  Tracer server1SkeletalTracer = Tracing.newBuilder()
    .localServiceName("server1")
    .addSpanHandler(new RetainSkeletalSpans())
    .addSpanHandler(toSpanHandler(skeletalSpans))
    .build().tracer();

  Tracer server2SkeletalTracer = Tracing.newBuilder()
    .localServiceName("server2")
    .addSpanHandler(new RetainSkeletalSpans())
    .addSpanHandler(toSpanHandler(skeletalSpans))
    .build().tracer();

  @Before public void acceptTwoServerRequests() {
    acceptTwoServerRequests(server1Tracer, server2Tracer);
    acceptTwoServerRequests(server1SkeletalTracer, server2SkeletalTracer);
  }

  @After public void close() {
    Tracing.current().close();
  }

  @Test public void skeletalSpans_skipLocalSpans() {
    assertThat(spans.values())
      .extracting(s -> s.stream().map(MutableSpan::name).collect(Collectors.toList()))
      .containsExactly(
        asList("post", "post", "controller", "get"),
        asList("async1"),
        asList("async2"),
        asList("post", "post", "post", "controller2", "get")
      );

    assertThat(skeletalSpans.values())
      .extracting(s -> s.stream().map(MutableSpan::name).collect(Collectors.toList()))
      .containsExactly(
        asList("post", "post", "get"),
        asList("post", "post", "post", "get")
      );
  }

  /** Simulates some service calls */
  static void acceptTwoServerRequests(Tracer server1Tracer, Tracer server2Tracer) {
    Span server1 = server1Tracer.newTrace().name("get").kind(Kind.SERVER).start();
    Span server2 = server1Tracer.newTrace().name("get").kind(Kind.SERVER).start();
    try {
      Span client1 =
        server1Tracer.newChild(server1.context()).name("post").kind(Kind.CLIENT).start();

      server2Tracer.joinSpan(fakeUseOfHeaders(client1.context()))
        .name("post")
        .kind(Kind.SERVER)
        .start().finish();

      ScopedSpan local1 = server1Tracer.startScopedSpanWithParent("controller", server1.context());
      try {
        try {
          server1Tracer.newTrace().name("async1").start().finish();
          server2Tracer.newTrace().name("async2").start().finish();

          ScopedSpan local2 =
            server1Tracer.startScopedSpanWithParent("controller2", server2.context());
          Span client2 = server1Tracer.nextSpan().name("post").kind(Kind.CLIENT).start();
          try {
            server2Tracer.joinSpan(fakeUseOfHeaders(client2.context()))
              .name("post")
              .kind(Kind.SERVER)
              .start().error(new RuntimeException()).finish();

            server1Tracer.nextSpan()
              .name("post")
              .kind(Kind.CLIENT)
              .start()
              .remoteServiceName("uninstrumentedServer")
              .error(new RuntimeException())
              .finish();
          } finally {
            client2.finish();
            local2.finish();
          }
        } finally {
          server2.finish();
        }
      } finally {
        client1.finish();
        local1.finish();
      }
    } finally {
      server1.finish();
    }
  }

  /** Ensures reporting is partitioned by trace ID */
  static SpanHandler toSpanHandler(Map<String, List<MutableSpan>> spans) {
    return new SpanHandler() {
      @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
        if (cause == Cause.ABANDONED) return true;
        spans.computeIfAbsent(span.traceId(), k -> new ArrayList<>()).add(span);
        return true;
      }
    };
  }

  /** To reduce code, we don't use real client-server instrumentation. This fakes headers. */
  static TraceContext fakeUseOfHeaders(TraceContext context) {
    return B3SingleFormat.parseB3SingleFormat(B3SingleFormat.writeB3SingleFormat(context))
      .context();
  }
}
