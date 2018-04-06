package brave.propagation;

import java.util.Collections;

/**
 * Useful when developing instrumentation as state is enforced more strictly.
 *
 * <p>For example, it is instance scoped as opposed to static scoped, not inheritable and throws an
 * exception if a scope is closed on a different thread that it was opened on.
 *
 * @see ThreadLocalCurrentTraceContext
 */
public final class StrictCurrentTraceContext extends ThreadLocalCurrentTraceContext {
  public StrictCurrentTraceContext() {
    // intentionally not inheritable to ensure instrumentation propagation doesn't accidentally work
    // intentionally not static to make explicit when instrumentation need per thread semantics
    super(Collections.singletonList(new StrictCurrentScopeDecorator()), new ThreadLocal<>());
  }
}
