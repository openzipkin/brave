package brave.dubbo.rpc;

import brave.Tracer;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public class ITTracingFilter_Producer extends ITTracingFilter {

  @Before public void setup() {
    setTracing(tracingBuilder(Sampler.ALWAYS_SAMPLE).build());
    server.start();

    client = new ReferenceConfig<>();
    client.setApplication(new ApplicationConfig("bean-consumer"));
    client.setFilter("tracing");
    client.setInterface(GreeterService.class);
    client.setUrl("dubbo://127.0.0.1:" + server.port() + "?scope=remote&generic=bean");
  }

  @Test public void propagatesSpan() throws Exception {
    client.get().sayHello("jorge");

    TraceContext context = server.takeRequest().context();
    assertThat(context.parentId()).isNull();
    assertThat(context.sampled()).isTrue();

    spans.take();
  }

  @Test public void makesChildOfCurrentSpan() throws Exception {
    brave.Span parent = tracing.tracer().newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(parent)) {
      client.get().sayHello("jorge");
    } finally {
      parent.finish();
    }

    TraceContext context = server.takeRequest().context();
    assertThat(context.traceId())
        .isEqualTo(parent.context().traceId());
    assertThat(context.parentId())
        .isEqualTo(parent.context().spanId());

    // we report one local and one client span
    assertThat(Arrays.asList(spans.take(), spans.take()))
        .extracting(Span::kind)
        .containsOnly(null, Span.Kind.CLIENT);
  }

  /**
   * This tests that the parent is determined at the time the request was made, not when the request
   * was executed.
   */
  @Test public void usesParentFromInvocationTime() throws Exception {
    server.enqueueDelay(TimeUnit.SECONDS.toMillis(1));

    brave.Span parent = tracing.tracer().newTrace().name("test").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(parent)) {
      RpcContext.getContext().asyncCall(new Callable<String>() {
        public String call() throws Exception {
          return client.get().sayHello("jorge");
        }
      });
      RpcContext.getContext().asyncCall(new Callable<String>() {
        public String call() throws Exception {
          return client.get().sayHello("romeo");
        }
      });
    } finally {
      parent.finish();
    }

    brave.Span otherSpan = tracing.tracer().newTrace().name("test2").start();
    try (Tracer.SpanInScope ws = tracing.tracer().withSpanInScope(otherSpan)) {
      for (int i = 0; i < 2; i++) {
        TraceContext context = server.takeRequest().context();
        assertThat(context.traceId())
            .isEqualTo(parent.context().traceId());
        assertThat(context.parentId())
            .isEqualTo(parent.context().spanId());
      }
    } finally {
      otherSpan.finish();
    }

    // Check we reported 2 local spans and 2 client spans
    assertThat(Arrays.asList(spans.take(), spans.take(), spans.take(), spans.take()))
        .extracting(Span::kind)
        .containsOnly(null, Span.Kind.CLIENT);
  }

  /** Unlike Brave 3, Brave 4 propagates trace ids even when unsampled */
  @Test public void propagates_sampledFalse() throws Exception {
    setTracing(tracingBuilder(Sampler.NEVER_SAMPLE).build());

    client.get().sayHello("jorge");
    TraceContextOrSamplingFlags extracted = server.takeRequest();
    assertThat(extracted.sampled()).isFalse();

    // @After will check that nothing is reported
  }

  @Test public void reportsClientKindToZipkin() throws Exception {
    client.get().sayHello("jorge");

    Span span = spans.take();
    assertThat(span.kind())
        .isEqualTo(Span.Kind.CLIENT);
  }

  @Test public void defaultSpanNameIsMethodName() throws Exception {
    client.get().sayHello("jorge");

    Span span = spans.take();
    assertThat(span.name())
        .isEqualTo("greeterservice/sayhello");
  }

  @Test public void onTransportException_addsErrorTag() throws Exception {
    server.stop();

    try {
      client.get().sayHello("jorge");
      failBecauseExceptionWasNotThrown(RpcException.class);
    } catch (RpcException e) {
    }

    Span span = spans.take();
    assertThat(span.tags().get("error"))
        .contains("RemotingException");
  }

  @Test public void onTransportException_addsErrorTag_async() throws Exception {
    server.stop();

    RpcContext.getContext().asyncCall(() -> client.get().sayHello("romeo"));

    Span span = spans.take();
    assertThat(span.tags().get("error"))
        .contains("RemotingException");
  }

  @Test public void flushesSpanOneWay() throws Exception {
    RpcContext.getContext().asyncCall(() -> {
      client.get().sayHello("romeo");
    });

    Span span = spans.take();
    assertThat(span.duration())
        .isNull();
  }

  @Test public void addsErrorTag_onUnimplemented() throws Exception {
    server.stop();
    server = new TestServer();
    server.service.setRef((method, parameterTypes, args) -> args);
    server.start();

    try {
      client.get().sayHello("jorge");
      failBecauseExceptionWasNotThrown(RpcException.class);
    } catch (RpcException e) {
    }

    Span span = spans.take();
    assertThat(span.tags().get("dubbo.error_code"))
        .isEqualTo("1");
    assertThat(span.tags().get("error"))
        .contains("Not found exported service");
  }
}
