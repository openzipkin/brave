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
package brave.propagation;

import brave.GarbageCollectors;
import brave.ScopedSpan;
import brave.Tracing;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.sampler.SamplerFunctions;
import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class StrictScopeDecoratorTest {
  StrictScopeDecorator decorator = StrictScopeDecorator.create();
  CurrentTraceContext currentTraceContext = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(decorator)
    .build();
  Tracing tracing = Tracing.newBuilder()
    .currentTraceContext(currentTraceContext)
    .build();
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();
  BusinessClass businessClass = new BusinessClass(tracing, decorator, context);

  @After public void close() {
    tracing.close();
  }

  @Test public void decorator_close_afterCorrectUsage() {
    try (Scope ws = currentTraceContext.newScope(null)) {
      try (Scope ws2 = currentTraceContext.newScope(context)) {
      }
    }

    decorator.close(); // doesn't error
  }

  @Test public void doesntDecorateNoop() {
    assertThat(decorator.decorateScope(context, Scope.NOOP)).isSameAs(Scope.NOOP);
    assertThat(decorator.decorateScope(null, Scope.NOOP)).isSameAs(Scope.NOOP);
    decorator.close(); // doesn't error
  }

  static class BusinessClass {
    final Tracing tracing;
    final ThreadLocalSpan threadLocalSpan;
    final ScopeDecorator decorator;
    final TraceContext context;

    BusinessClass(Tracing tracing, ScopeDecorator decorator, TraceContext context) {
      this.tracing = tracing;
      this.threadLocalSpan = ThreadLocalSpan.create(tracing.tracer());
      this.decorator = decorator;
      this.context = context;
    }

    Closeable businessMethodAdHoc() {
      return decorator.decorateScope(context, mock(Scope.class));
    }

    Closeable businessMethodNewScope() {
      return tracing.currentTraceContext().newScope(context);
    }

    Closeable businessMethodMaybeScope() {
      return tracing.currentTraceContext().maybeScope(context);
    }

    Closeable businessMethodWithSpanInScope() {
      return tracing.tracer().withSpanInScope(tracing.tracer().nextSpan());
    }

    Closeable businessMethodStartScopedSpan() {
      ScopedSpan span = tracing.tracer().startScopedSpan("foo");
      return span::finish;
    }

    Closeable businessMethodStartScopedSpan_sampler() {
      ScopedSpan span =
        tracing.tracer().startScopedSpan("foo", SamplerFunctions.neverSample(), false);
      return span::finish;
    }

    Closeable businessMethodStartScopedSpanWithParent() {
      ScopedSpan span = tracing.tracer().startScopedSpanWithParent("foo", null);
      return span::finish;
    }

    Closeable businessMethodThreadLocalSpan() {
      threadLocalSpan.next();
      return threadLocalSpan::remove;
    }
  }

  @Test public void scope_close_onWrongThread_adHoc() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodAdHoc, "businessMethodAdHoc");
  }

  @Test public void decorator_close_withLeakedScope_adHoc() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodAdHoc, "businessMethodAdHoc");
  }

  @Test public void scope_close_onWrongThread_newScope() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodNewScope, "businessMethodNewScope");
  }

  @Test public void decorator_close_withLeakedScope_onWrongThread_newScope() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodNewScope,
      "businessMethodNewScope");
  }

  @Test public void scope_close_onWrongThread_maybeScope() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodMaybeScope, "businessMethodMaybeScope");
  }

  @Test public void decorator_close_withLeakedScope_maybeScope() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodMaybeScope,
      "businessMethodMaybeScope");
  }

  @Test public void scope_close_onWrongThread_withSpanInScope() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodWithSpanInScope,
      "businessMethodWithSpanInScope");
  }

  @Test public void decorator_close_withLeakedScope_withSpanInScope() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodWithSpanInScope,
      "businessMethodWithSpanInScope");
  }

  @Test public void scope_close_onWrongThread_startScopedSpan() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodStartScopedSpan,
      "businessMethodStartScopedSpan");
  }

  @Test public void decorator_close_withLeakedScope_startScopedSpan() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodStartScopedSpan,
      "businessMethodStartScopedSpan");
  }

  @Test public void scope_close_onWrongThread_startScopedSpan_sampler() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodStartScopedSpan_sampler,
      "businessMethodStartScopedSpan_sampler");
  }

  @Test public void decorator_close_withLeakedScope_startScopedSpan_sampler() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodStartScopedSpan_sampler,
      "businessMethodStartScopedSpan_sampler");
  }

  @Test public void scope_close_onWrongThread_startScopedSpanWithParent() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodStartScopedSpanWithParent,
      "businessMethodStartScopedSpanWithParent");
  }

  @Test public void decorator_close_withLeakedScope_startScopedSpanWithParent() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodStartScopedSpanWithParent,
      "businessMethodStartScopedSpanWithParent");
  }

  // ThreadLocalSpan by definition can't be closed on another thread
  @Test(expected = AssertionError.class)
  public void scope_close_onWrongThread_threadLocalSpan() throws Exception {
    scope_close_onWrongThread(businessClass::businessMethodThreadLocalSpan,
      "businessMethodThreadLocalSpan");
  }

  @Test public void decorator_close_withLeakedScope_threadLocalSpan() throws Exception {
    decorator_close_withLeakedScope(businessClass::businessMethodThreadLocalSpan,
      "businessMethodThreadLocalSpan");
  }

  void scope_close_onWrongThread(Supplier<Closeable> method, String methodName) throws Exception {
    AtomicReference<Closeable> closeable = new AtomicReference<>();
    Thread t1 = new Thread(() -> closeable.set(method.get()));
    t1.setName("t1");
    t1.start();
    t1.join();

    AtomicReference<Throwable> errorCatcher = new AtomicReference<>();

    Thread t2 = new Thread(() -> {
      try {
        closeable.get().close();
      } catch (Throwable t) {
        errorCatcher.set(t);
      }
    });
    t2.setName("t2");
    t2.start();
    t2.join();

    assertThat(errorCatcher.get())
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Thread [t1] opened scope, but thread [t2] closed it")
      .satisfies(e -> assertThat(e.getCause().getMessage())
        .matches("Thread \\[t1\\] opened scope for [0-9a-f]{16}/[0-9a-f]{16} here:"))
      .satisfies(e -> assertStackTraceStartsWithMethod(e.getCause(), methodName));
  }

  void decorator_close_withLeakedScope(Supplier<Closeable> method, String methodName)
    throws Exception {
    Thread thread = new Thread(method::get);
    thread.setName("t1");
    thread.start();
    thread.join();

    assertThatThrownBy(decorator::close)
      .isInstanceOf(AssertionError.class)
      .satisfies(t -> assertThat(t.getMessage())
        .matches("Thread \\[t1\\] opened a scope of [0-9a-f]{16}/[0-9a-f]{16} here:"))
      .hasNoCause()
      .satisfies(t -> assertStackTraceStartsWithMethod(t, methodName));
  }

  static void assertStackTraceStartsWithMethod(Throwable throwable, String methodName) {
    assertThat(throwable.getStackTrace()[0].getMethodName())
      .isEqualTo(methodName);
  }

  @Test public void garbageCollectedScope() throws Exception {
    currentTraceContext.newScope(null);
    GarbageCollectors.blockOnGC();

    // We cleanup the weak references on scope operations, so next one will fail.
    assertThatThrownBy(() -> currentTraceContext.newScope(null))
      .isInstanceOf(AssertionError.class)
      .hasMessage("Thread [main] opened a scope of null here:");
  }
}
