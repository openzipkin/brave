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
package brave.features.handler;

import brave.ScopedSpan;
import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
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
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.internal.DependencyLinker;
import zipkin2.reporter.Reporter;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This shows how you can skip local spans to reduce the cost of storage. In this example we are
 * cherry-picking data used by the dependency linker, mostly to make it simpler.
 */
public class SkeletalSpansTest {

  class ReportSkeletalSpans extends FinishedSpanHandler {
    final String localServiceName;
    final Reporter<zipkin2.Span> delegate;

    ReportSkeletalSpans(String localServiceName, Reporter<zipkin2.Span> delegate) {
      this.localServiceName = localServiceName;
      this.delegate = delegate;
    }

    @Override public boolean handle(TraceContext context, MutableSpan span) {
      if (span.kind() == null) return false; // skip local spans

      zipkin2.Span.Builder builder = zipkin2.Span.newBuilder()
        .traceId(context.traceIdString())
        .parentId(
          context.isLocalRoot() ? null : context.localRootIdString()) // rewrite the parent ID
        .id(context.spanIdString())
        .name(span.name())
        .kind(zipkin2.Span.Kind.valueOf(span.kind().name()))
        .localEndpoint(Endpoint.newBuilder().serviceName(localServiceName).build());

      if (span.error() != null || span.tag("error") != null) {
        builder.putTag("error", ""); // linking counts errors: the value isn't important
      }
      if (span.remoteServiceName() != null) {
        builder.remoteEndpoint(Endpoint.newBuilder().serviceName(span.remoteServiceName()).build());
      }

      delegate.report(builder.build());
      return false; // end of the line
    }
  }

  Map<String, List<zipkin2.Span>>
    spans = new LinkedHashMap<>(),
    skeletalSpans = new LinkedHashMap<>();

  Tracer server1Tracer = Tracing.newBuilder()
    .localServiceName("server1")
    .spanReporter(toReporter(spans))
    .build().tracer();

  Tracer server2Tracer = Tracing.newBuilder()
    .localServiceName("server2")
    .spanReporter(toReporter(spans))
    .build().tracer();

  Tracer server1SkeletalTracer = Tracing.newBuilder()
    .addFinishedSpanHandler(new ReportSkeletalSpans("server1", toReporter(skeletalSpans)))
    .spanReporter(Reporter.NOOP)
    .build().tracer();

  Tracer server2SkeletalTracer = Tracing.newBuilder()
    .addFinishedSpanHandler(new ReportSkeletalSpans("server2", toReporter(skeletalSpans)))
    .spanReporter(Reporter.NOOP)
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
      .extracting(s -> s.stream().map(zipkin2.Span::name).collect(Collectors.toList()))
      .containsExactly(
        asList("post", "post", "controller", "get"),
        asList("async1"),
        asList("async2"),
        asList("post", "post", "post", "controller2", "get")
      );

    assertThat(skeletalSpans.values())
      .extracting(s -> s.stream().map(zipkin2.Span::name).collect(Collectors.toList()))
      .containsExactly(
        asList("post", "post", "get"),
        asList("post", "post", "post", "get")
      );
  }

  @Test public void skeletalSpans_produceSameServiceGraph() {
    assertThat(link(spans))
      .containsExactly(
        DependencyLink.newBuilder()
          .parent("server1")
          .child("server2")
          .callCount(2L)
          .errorCount(1L)
          .build(),
        DependencyLink.newBuilder()
          .parent("server1")
          .child("uninstrumentedserver")
          .callCount(1L)
          .errorCount(1L)
          .build()
      )
      .isEqualTo(link(skeletalSpans));
  }

  /** Executes the linker for each collected trace */
  static List<DependencyLink> link(Map<String, List<zipkin2.Span>> spans) {
    DependencyLinker linker = new DependencyLinker();
    spans.values().forEach(trace -> linker.putTrace(trace));
    return linker.link();
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
  static Reporter<zipkin2.Span> toReporter(Map<String, List<zipkin2.Span>> spans) {
    return s -> spans.computeIfAbsent(s.traceId(), k -> new ArrayList<>()).add(s);
  }

  /** To reduce code, we don't use real client-server instrumentation. This fakes headers. */
  static TraceContext fakeUseOfHeaders(TraceContext context) {
    return B3SingleFormat.parseB3SingleFormat(B3SingleFormat.writeB3SingleFormat(context))
      .context();
  }
}
