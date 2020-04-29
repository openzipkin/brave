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
package brave.test.propagation;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.internal.Nullable;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.test.util.ClassLoaders;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadableWithSupplier;
import static brave.test.util.ClassLoaders.newInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

public abstract class CurrentTraceContextTest {
  protected static final SingleCorrelationField CORRELATION_FIELD =
    SingleCorrelationField.create(BaggageField.create("userId"));

  Propagation.Factory baggageFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .add(SingleBaggageField.remote(CORRELATION_FIELD.baggageField())).build();

  protected final CurrentTraceContext currentTraceContext;
  protected final TraceContext context = baggageFactory.decorate(
    TraceContext.newBuilder().traceIdHigh(-1L).traceId(1L).spanId(1L).sampled(true).build()
  );
  protected final TraceContext notYetSampledContext =
    TraceContext.newBuilder().traceId(2L).spanId(1L).build();
  protected final TraceContext unsampledContext =
    TraceContext.newBuilder().traceId(2L).spanId(2L).sampled(false).build();

  protected abstract Class<? extends Supplier<CurrentTraceContext.Builder>> builderSupplier();

  protected CurrentTraceContextTest() {
    currentTraceContext = newBuilder().build();
  }

  protected void verifyImplicitContext(@Nullable TraceContext context) {
  }

  private CurrentTraceContext.Builder newBuilder() {
    return newInstance(builderSupplier(), getClass().getClassLoader()).get();
  }

  @Test public void currentSpan_defaultsToNull() {
    assertThat(currentTraceContext.get()).isNull();
  }

  @Test public void newScope_retainsContext() {
    retainsContext(currentTraceContext.newScope(context));
  }

  @Test public void maybeScope_retainsContext() {
    retainsContext(currentTraceContext.maybeScope(context));
  }

  void retainsContext(Scope scope) {
    try {
      assertThat(scope).isNotEqualTo(Scope.NOOP);
      assertThat(currentTraceContext.get())
        .isEqualTo(context);
      verifyImplicitContext(context);
    } finally {
      scope.close();
    }
  }

  @Test public void newScope_noticesDifferentBaggageField() {
    try (Scope scope = currentTraceContext.newScope(context)) {
      TraceContext differentBaggageField = context.toBuilder().build();
      CORRELATION_FIELD.baggageField().updateValue(differentBaggageField, "foo");

      try (Scope scope2 = currentTraceContext.newScope(differentBaggageField)) {
        assertThat(scope2).isNotEqualTo(Scope.NOOP);
        assertThat(currentTraceContext.get())
          .isEqualTo(differentBaggageField);
        verifyImplicitContext(differentBaggageField);
      }
    }
  }

  @Test public void ignoresNoopScopeDecorator() {
    ScopeDecorator one = (context, scope) -> scope;

    CurrentTraceContext shouldHaveOnlyOne = newBuilder()
      .addScopeDecorator(ScopeDecorator.NOOP)
      .addScopeDecorator(one).build();

    assertThat(shouldHaveOnlyOne).extracting("scopeDecorators")
      .asInstanceOf(InstanceOfAssertFactories.ARRAY)
      .doesNotContain(ScopeDecorator.NOOP);
  }

  @Test public void newScope_decoratesWithDifferentScope() {
    Scope differentScope = () -> {
    };

    CurrentTraceContext decoratesWithDifferentScope = newBuilder()
      .addScopeDecorator((context, scope) -> differentScope).build();

    try (Scope scope = decoratesWithDifferentScope.newScope(context)) {
      assertThat(scope).isSameAs(differentScope);
    }

    try (Scope scope = decoratesWithDifferentScope.newScope(null)) {
      assertThat(scope).isSameAs(differentScope);
    }
  }

  @Test public void maybeScope_decoratesWithDifferentScope() {
    Scope differentScope = () -> {
    };

    CurrentTraceContext decoratesWithDifferentScope = newBuilder()
      .addScopeDecorator((context, scope) -> differentScope).build();

    try (Scope noop = Scope.NOOP) {
      try (Scope scope = decoratesWithDifferentScope.maybeScope(context)) {
        assertThat(scope).isSameAs(differentScope);
      }
    }

    try (Scope noop = Scope.NOOP) {
      try (Scope scope = decoratesWithDifferentScope.maybeScope(null)) {
        assertThat(scope).isSameAs(differentScope);
      }
    }
  }

  @Test public void newScope_noticesDifferentSpanId() {
    noticesDifferentSpanId(currentTraceContext.newScope(context));
  }

  @Test public void maybeScope_noticesDifferentSpanId() {
    noticesDifferentSpanId(currentTraceContext.maybeScope(context));
  }

  void noticesDifferentSpanId(Scope scope) {
    TraceContext differentSpanId = context.toBuilder().spanId(context.spanId() + 1L).build();
    try (Scope scope2 = currentTraceContext.maybeScope(differentSpanId)) {
      assertThat(scope2).isNotEqualTo(Scope.NOOP);
      assertThat(currentTraceContext.get())
        .isEqualTo(differentSpanId);
      verifyImplicitContext(differentSpanId);
    } finally {
      scope.close();
    }
  }

  @Test public void newScope_noticesDifferentContext() {
    noticesDifferentContext(currentTraceContext.newScope(context));
  }

  @Test public void maybeScope_noticesDifferentContext() {
    noticesDifferentContext(currentTraceContext.maybeScope(context));
  }

  void noticesDifferentContext(Scope scope) {
    try (Scope scope2 = currentTraceContext.maybeScope(unsampledContext)) {
      assertThat(scope2).isNotEqualTo(Scope.NOOP);
      assertThat(currentTraceContext.get())
        .isEqualTo(unsampledContext);
      verifyImplicitContext(unsampledContext);
      try (Scope scope3 = currentTraceContext.maybeScope(notYetSampledContext)) {
        assertThat(scope3).isNotEqualTo(Scope.NOOP);
        assertThat(currentTraceContext.get())
          .isEqualTo(notYetSampledContext);
        verifyImplicitContext(notYetSampledContext);
      }
    } finally {
      scope.close();
    }
  }

  @Test public void maybeScope_doesntDuplicateContext() {
    try (Scope scope = currentTraceContext.newScope(context)) {
      try (Scope scope2 = currentTraceContext.maybeScope(context)) {
        assertThat(scope2).isEqualTo(Scope.NOOP);
      }
    }
  }

  @Test public void newScope_canClearScope() {
    canClearScope(() -> currentTraceContext.newScope(null));
  }

  @Test public void maybeScope_canClearScope() {
    canClearScope(() -> currentTraceContext.maybeScope(null));
  }

  @Test public void maybeScope_doesntDuplicateContext_onNull() {
    try (Scope scope2 = currentTraceContext.maybeScope(null)) {
      assertThat(scope2).isEqualTo(Scope.NOOP);
    }
  }

  void canClearScope(Supplier<Scope> noScoper) {
    try (Scope scope = currentTraceContext.newScope(context)) {
      try (Scope noScope = noScoper.get()) {
        assertThat(noScope).isNotEqualTo(Scope.NOOP);
        assertThat(currentTraceContext.get())
          .isNull();
        verifyImplicitContext(null);
      }

      // old context reverted
      assertThat(currentTraceContext.get())
        .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  protected void is_inheritable(CurrentTraceContext inheritableCurrentTraceContext)
    throws Exception {
    // use a single-threaded version of newCachedThreadPool
    ExecutorService service = new ThreadPoolExecutor(0, 1,
      60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());

    // submitting a job grows the pool, attaching the context to its thread
    try (Scope scope = inheritableCurrentTraceContext.newScope(context)) {
      assertThat(service.submit(inheritableCurrentTraceContext::get).get())
        .isEqualTo(context);
    }

    // same thread executes the next job and still has the same context (leaked and not cleaned up)
    assertThat(service.submit(inheritableCurrentTraceContext::get).get())
      .isEqualTo(context);

    service.shutdownNow();
  }

  @Test public void isnt_inheritable() throws Exception {
    ExecutorService service = Executors.newCachedThreadPool();

    try (Scope scope = currentTraceContext.newScope(context)) {
      assertThat(service.submit(() -> {
        verifyImplicitContext(null);
        return currentTraceContext.get();
      }).get()).isNull();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof Error) throw (Error) e.getCause();
      throw (Exception) e.getCause();
    }

    assertThat(service.submit(currentTraceContext::get).get())
      .isNull();
    verifyImplicitContext(null);

    service.shutdownNow();
  }

  @Test public void attachesSpanInCallable_canClear() throws Exception {
    Callable<?> callable = currentTraceContext.wrap(() -> {
      assertThat(currentTraceContext.get()).isNull();
      verifyImplicitContext(null);
      return true;
    });

    // Set another span between the time the task was made and executed.
    try (Scope scope2 = currentTraceContext.newScope(unsampledContext)) {
      callable.call(); // runs assertion
      verifyImplicitContext(unsampledContext);
    }
  }

  @Test public void attachesSpanInCallable() throws Exception {
    Callable<?> callable;
    try (Scope scope = currentTraceContext.newScope(context)) {
      callable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get()).isEqualTo(context);
        verifyImplicitContext(context);
        return true;
      });

      callable.call(); // runs assertion in the same scope
    }

    // Set another span between the time the task was made and executed.
    try (Scope scope2 = currentTraceContext.newScope(unsampledContext)) {
      callable.call(); // runs assertion
      verifyImplicitContext(unsampledContext);
    }
  }

  @Test public void restoresSpanAfterCallable() throws Exception {
    try (Scope scope0 = currentTraceContext.newScope(context)) {
      attachesSpanInCallable();
      assertThat(currentTraceContext.get())
        .isEqualTo(context);
      verifyImplicitContext(context);
    }
  }

  @Test public void attachesSpanInRunnable() {
    Runnable runnable;
    try (Scope scope = currentTraceContext.newScope(context)) {
      runnable = currentTraceContext.wrap(() -> {
        assertThat(currentTraceContext.get())
          .isEqualTo(context);
        verifyImplicitContext(context);
      });

      runnable.run(); // runs assertion in the same scope
    }

    // Set another span between the time the task was made and executed.
    try (Scope scope2 = currentTraceContext.newScope(unsampledContext)) {
      runnable.run(); // runs assertion
      verifyImplicitContext(unsampledContext);
    }
  }

  @Test public void restoresSpanAfterRunnable() throws Exception {
    TraceContext context0 = TraceContext.newBuilder().traceId(3L).spanId(3L).build();

    try (Scope scope0 = currentTraceContext.newScope(context0)) {
      attachesSpanInRunnable();
      assertThat(currentTraceContext.get())
        .isEqualTo(context0);
      verifyImplicitContext(context0);
    }
  }

  @Test public void unloadable_unused() {
    assertRunIsUnloadableWithSupplier(Unused.class, builderSupplier());
  }

  static class Unused extends ClassLoaders.ConsumerRunnable<CurrentTraceContext.Builder> {
    @Override public void accept(CurrentTraceContext.Builder builder) {
      builder.build();
    }
  }

  @Test public void unloadable_afterScopeClose() {
    assertRunIsUnloadableWithSupplier(ClosedScope.class, builderSupplier());
  }

  static class ClosedScope extends ClassLoaders.ConsumerRunnable<CurrentTraceContext.Builder> {
    @Override public void accept(CurrentTraceContext.Builder builder) {
      CurrentTraceContext current = builder.build();
      try (Scope ws = current.newScope(TraceContext.newBuilder().traceId(1L).spanId(2L).build())) {
      }
    }
  }

  /**
   * TODO: While it is an instrumentation bug to not close a scope, we should be tolerant. For
   * example, considering weak references or similar.
   */
  @SuppressWarnings("CheckReturnValue")
  @Test public void notUnloadable_whenScopeLeaked() {
    try {
      assertRunIsUnloadableWithSupplier(LeakedScope.class, builderSupplier());
      failBecauseExceptionWasNotThrown(AssertionError.class);
    } catch (AssertionError e) {
      // clear the leaked scope so other tests don't break
      currentTraceContext.newScope(null);
    }
  }

  static class LeakedScope extends ClassLoaders.ConsumerRunnable<CurrentTraceContext.Builder> {
    @Override public void accept(CurrentTraceContext.Builder builder) {
      builder.build().newScope(TraceContext.newBuilder().traceId(1L).spanId(2L).build());
    }
  }
}
