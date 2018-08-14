package brave.propagation;

/**
 * Useful when developing instrumentation as state is enforced more strictly.
 *
 * <p>For example, it is instance scoped as opposed to static scoped, not inheritable and throws an
 * exception if a scope is closed on a different thread that it was opened on.
 *
 * @deprecated use {@linkplain StrictScopeDecorator}. This will be removed in Brave v6.
 */
@Deprecated
public final class StrictCurrentTraceContext extends ThreadLocalCurrentTraceContext {
  static final CurrentTraceContext.Builder SCOPE_DECORATING_BUILDER =
      ThreadLocalCurrentTraceContext.newBuilder().addScopeDecorator(new StrictScopeDecorator());

  public StrictCurrentTraceContext() { // Preserve historical public ctor
    // intentionally not inheritable to ensure instrumentation propagation doesn't accidentally work
    // intentionally not static to make explicit when instrumentation need per thread semantics
    super(SCOPE_DECORATING_BUILDER, new ThreadLocal<>());
  }
}
