/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.baggage;

import brave.internal.CorrelationContext;
import brave.propagation.CurrentTraceContext.Scope;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static brave.baggage.CorrelationScopeDecorator.equal;

/** Sets up thread locals needed to support {@link CorrelationScopeConfig#flushOnUpdate()} */
final class CorrelationFlushScope extends AtomicBoolean implements Scope {
  final CorrelationUpdateScope updateScope;

  CorrelationFlushScope(CorrelationUpdateScope updateScope) {
    this.updateScope = updateScope;
    pushCurrentUpdateScope(updateScope);
  }

  @Override public void close() {
    // don't allow misalignment when close is called multiple times.
    if (!compareAndSet(false, true)) return;
    popCurrentUpdateScope(updateScope);
    updateScope.close();
  }

  /**
   * Handles a flush by synchronizing the correlation context followed by signaling each stacked
   * scope about a potential field update.
   *
   * <p>Overhead here occurs on the calling thread. Ex. the one that calls {@link
   * BaggageField#updateValue(String)}.
   */
  static void flush(BaggageField field, String value) {
    Set<CorrelationContext> syncedContexts = new LinkedHashSet<CorrelationContext>();
    for (Object o : updateScopeStack()) {
      CorrelationUpdateScope next = ((CorrelationUpdateScope) o);
      String name = next.name(field);
      if (name == null) continue;

      // Since this is a static method, it could be called with different tracers on the stack.
      // This synchronizes the context if we haven't already.
      if (!syncedContexts.contains(next.context)) {
        if (!equal(next.context.getValue(name), value)) {
          next.context.update(name, value);
        }
        syncedContexts.add(next.context);
      }

      // Now, signal the current scope in case it has a value change
      next.handleUpdate(field, value);
    }
  }

  static final ThreadLocal<ArrayDeque<Object>> updateScopeStack =
    new ThreadLocal<ArrayDeque<Object>>();

  static ArrayDeque<Object> updateScopeStack() {
    ArrayDeque<Object> stack = updateScopeStack.get();
    if (stack == null) {
      stack = new ArrayDeque<Object>();
      updateScopeStack.set(stack);
    }
    return stack;
  }

  static void pushCurrentUpdateScope(CorrelationUpdateScope updateScope) {
    updateScopeStack().push(updateScope);
  }

  static void popCurrentUpdateScope(CorrelationUpdateScope expected) {
    Object popped = updateScopeStack().pop();
    assert equal(popped, expected) :
      "Misalignment: popped updateScope " + popped + " !=  expected " + expected;
  }
}
