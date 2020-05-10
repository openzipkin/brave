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
package brave.features.sampler;

import brave.ScopedSpan;
import brave.Tracer;
import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import brave.sampler.DeclarativeSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.test.TestSpanHandler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AspectJSamplerTest.Config.class)
public class AspectJSamplerTest {

  // Don't use static configuration in real life. This is only to satisfy the unit test runner
  static TestSpanHandler spans = new TestSpanHandler();
  static AtomicReference<Tracing> tracing = new AtomicReference<>();

  @Autowired Service service;

  @Before public void clear() {
    tracing.set(Tracing.newBuilder()
      .currentTraceContext(StrictCurrentTraceContext.create())
      .addSpanHandler(spans)
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

  @Component @Aspect static class TracingAspect {
    SamplerFunction<Traced> samplerFunction = DeclarativeSampler.createWithRate(Traced::sampleRate);

    @Around("@annotation(traced)")
    public Object traceThing(ProceedingJoinPoint pjp, Traced traced) throws Throwable {
      Tracer tracer = tracing.get().tracer();

      // When there is no trace in progress, this overrides the decision based on the annotation
      ScopedSpan span = tracer.startScopedSpan(spanName(pjp), samplerFunction, traced);
      try {
        return pjp.proceed();
      } catch (RuntimeException | Error e) {
        span.error(e);
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

    @Traced(sampleRate = 0) public void notTraced() {
    }
  }

  @Retention(RetentionPolicy.RUNTIME) public @interface Traced {
    int sampleRate() default 10;
  }

  static String spanName(ProceedingJoinPoint pjp) {
    return pjp.getSignature().getName();
  }
}
