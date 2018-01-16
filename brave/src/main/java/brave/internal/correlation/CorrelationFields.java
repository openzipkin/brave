package brave.internal.correlation;

import brave.internal.Nullable;
import brave.propagation.Propagation;
import java.util.Map;

/**
 * Associates key/value pairs with the current trace context. Correlation fields are stored as a
 * part of the trace context, adding overhead. For this reason, {@link #isNoop() noop mode} is
 * default: correlation fields are opt-in.
 *
 * <p>Here's an example of instrumentation publishing a user ID field for downstream code to read
 * with {@link CurrentCorrelationFields}.
 * <pre>{@code
 * // mutate the trace context's correlation fields
 * span.context().correlationFields().set("user-id", currentUserId);
 *
 * // set the context in scope
 * try (Scope scope = tracing.currentTraceContext().newScope(traceContext)) {
 *   // downstream can read fields with CurrentCorrelationFields.get("user-id")
 *   //                              or CurrentCorrelationFields.forEach((k, v) ->{});
 * }
 * // Repeating the same block might have different results as downstream code
 * // or other threads can overwrite the key name "method"
 * }</pre>
 *
 * <h3>Data constraints</h3>
 *
 * <p>This implementation is strict with regards to key and value policy. Currently limited to 255
 * printable characters respectively. That said, there's no requirement for fields to end up on the
 * wire with the same names. For example, in <a href="https://github.com/w3c/distributed-tracing/tree/master/correlation_context">Correlation-Context</a>,
 * they are embedded in a single header, joined on semi-colon.
 *
 * <h3>Relationship to Span tags</h3>
 *
 * <p>{@link brave.Span#tag(String, String) tags} are different than correlation fields in the
 * following ways:
 * <pre>
 * <ul>
 *   <li>Tags are sent out-of-band where correlation fields are in-band, usually embedded in request headers</li>
 *   <li>As correlation fields are used to synchronize other contexts, they support readback</li>
 * </ul>
 * </pre>
 *
 * Put another way, a metrics or logging context can readily access correlation fields once they are
 * added.
 *
 * <h3>Relationship to Propagation injection and extraction</h3>
 * {@link Propagation} injection and extraction is intentionally decoupled from correlation fields.
 * The reason for this is we don't know what the encoding format would be, and if it correlation
 * fields will be in a separate propagation field or not. For example, if the propagation format is
 * Amazon's, correlation fields coexist in the same header field as trace identifiers. B3 has no
 * correlation field format. However, you can add <a href="https://github.com/w3c/distributed-tracing/tree/master/correlation_context">Correlation-Context</a>
 * to B3 to accomplish both.
 *
 * <h3>Application notes</h3>
 *
 * <p>Where possible, instrument nearest to the original request. For example, ordering before
 * metrics can allow any extracted fields to be visible as a metrics dimension or logging context key.
 *
 * <p>Do not use correlation fields as ad-hoc storage, as they will add weight to the request and
 * expose them to arbitrary access. A password would be a very bad example of a correlation field.
 */
public abstract class CorrelationFields implements Cloneable {

  /** When true, no fields are recorded. Use this flag to avoid performing expensive computation. */
  public abstract boolean isNoop();

  // cannot depend on java 8 apis
  public interface Consumer {
    void accept(String name, String value);
  }

  public abstract void set(String name, String value);

  /** Hidden as used for internal merging */
  public abstract void setAll(CorrelationFields other);

  /** Retrieves a correlation key by name or returns null if not found. */
  @Nullable public abstract String get(String name);

  public abstract boolean isEmpty();

  /**
   * Invokes the consumer for each propagation field.
   *
   * This is similar to {@link Map#forEach(java.util.function.BiConsumer)} and used to synchronize contexts like MDC.
   */
  public abstract void forEach(Consumer consumer);

  @Override public abstract CorrelationFields clone();

  public static final CorrelationFields NOOP = new CorrelationFields() {

    @Override public boolean isNoop() {
      return true;
    }

    @Override public void set(String name, String value) {
    }

    @Override public void setAll(CorrelationFields other) {
    }

    @Override public String get(String name) {
      return null;
    }

    @Override public boolean isEmpty() {
      return true;
    }

    @Override public void forEach(Consumer consumer) {
    }

    @Override public CorrelationFields clone() {
      return this;
    }

    @Override public String toString() {
      return "NoopCorrelationFields{}";
    }
  };

  CorrelationFields() {
  }
}
