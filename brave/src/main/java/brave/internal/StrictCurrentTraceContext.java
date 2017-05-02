package brave.internal;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

/** Useful when developing instrumentation as state is enforced more strictly */
public final class StrictCurrentTraceContext extends CurrentTraceContext {
  // intentionally not inheritable to ensure instrumentation propagation doesn't accidentally work
  // intentionally not static to make explicit when instrumentation need per thread semantics
  final ThreadLocal<TraceContext> local = new ThreadLocal<>();

  @Override public TraceContext get() {
    return local.get();
  }

  /** Identifies problems by throwing assertion errors when a scope is closed on a different thread. */
  @Override public Scope newScope(TraceContext currentSpan) {
    TraceContext previous = local.get();
    local.set(currentSpan);
    return new StrictScope(previous, new Error(String.format("Thread %s opened scope for %s here:",
        Thread.currentThread().getName(), currentSpan)));
  }

  class StrictScope implements Scope {
    final TraceContext previous;
    final Throwable caller;
    final long threadId = Thread.currentThread().getId();

    StrictScope(TraceContext previous, Throwable caller) {
      this.previous = previous;
      this.caller = caller;
    }

    @Override public void close() {
      if (Thread.currentThread().getId() != threadId) {
        throw new IllegalStateException(
            "scope closed in a different thread: " + Thread.currentThread().getName(),
            caller);
      }
      local.set(previous);
    }

    @Override public String toString() {
      return caller.toString();
    }
  }
}