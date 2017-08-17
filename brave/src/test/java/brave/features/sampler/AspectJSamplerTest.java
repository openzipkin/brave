package brave.features.sampler;

import brave.Tracer;
import brave.Tracing;
import brave.sampler.DeclarativeSampler;
import brave.sampler.Sampler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import zipkin.Constants;
import zipkin.Span;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AspectJSamplerTest.Config.class)
public class AspectJSamplerTest {

  // Don't use static configuration in real life. This is only to satisfy the unit test runner
  static List<Span> spans = new ArrayList<>();
  static AtomicReference<Tracing> tracing = new AtomicReference<>();

  @Autowired Service service;

  @Before public void clear() {
    tracing.set(Tracing.newBuilder()
        .reporter(spans::add)
        .sampler(new Sampler() {
          @Override public boolean isSampled(long traceId) {
            throw new AssertionError(); // in this case, we aren't expecting a fallback
          }
        }).build());
    spans.clear();
  }

  @After public void close() {
    Tracing currentTracing = tracing.get();
    if (currentTracing != null) currentTracing.close();
  }

  @Test public void traced() {
    service.traced();

    assertThat(spans).isNotEmpty();
  }

  @Test public void notTraced() {
    service.notTraced();

    assertThat(spans).isEmpty();
  }

  @Configuration
  @EnableAspectJAutoProxy
  @Import({Service.class, TracingAspect.class})
  static class Config {
  }

  @Component
  @Aspect
  static class TracingAspect {
    DeclarativeSampler<Traced> sampler = DeclarativeSampler.create(Traced::sampleRate);

    @Around("@annotation(traced)")
    public Object traceThing(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
      Tracer tracer = tracing.get().tracer();
      // simplification as starts a new trace always
      brave.Span span = tracer.newTrace(sampler.sample(traced)).name("").start();
      try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        return pjp.proceed();
      } catch (RuntimeException | Error e) {
        span.tag(Constants.ERROR, e.getMessage());
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  @Component // aop only works for public methods.. the type can be package private though
  static class Service {
    // these two methods set different rates. This shows that instances are independent
    @Traced public void traced() {
    }

    @Traced(sampleRate = 0.0f) public void notTraced() {
    }
  }

  @Retention(RetentionPolicy.RUNTIME) public @interface Traced {
    float sampleRate() default 1.0f;
  }
}
