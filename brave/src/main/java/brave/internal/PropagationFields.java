package brave.internal;

import brave.propagation.TraceContext;
import java.util.List;
import java.util.Map;

/** Copy-on-write keeps propagation changes in a child context from affecting its parent */
public abstract class PropagationFields {
  private TraceContext context; // guarded by this

  /** Returns the value of the field with the specified key or null if not available */
  public abstract String get(String name);

  /** Replaces the value of the field with the specified key, ignoring if not a permitted field */
  public abstract void put(String name, String value);

  /** for each field in the input replace the value if the key doesn't already exist */
  abstract void putAllIfAbsent(PropagationFields parent);

  public abstract Map<String, String> toMap();

  /** Fields are extracted before a context is created. We need to lazy set the context */
  final boolean tryAssociate(TraceContext newContext) {
    synchronized (this) {
      if (context == null) {
        context = newContext;
        return true;
      }
      return context.traceId() == newContext.traceId()
          && context.spanId() == newContext.spanId();
    }
  }

  @Override public String toString() {
    return getClass().getSimpleName() + toMap();
  }

  /** Returns the value of the field with the specified key or null if not available */
  public static String get(TraceContext context, String name) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    PropagationFields fields = find(context.extra());
    return fields != null ? fields.get(name) : null;
  }

  /** Replaces the value of the field with the specified key, ignoring if not a permitted field */
  public static void put(TraceContext context, String name, String value) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    if (value == null) throw new NullPointerException("value == null");
    PropagationFields fields = find(context.extra());
    if (fields == null) return;
    fields.put(name, value);
  }

  public static PropagationFields find(List<Object> extra) {
    for (int i = 0, length = extra.size(); i < length; i++) {
      Object next = extra.get(i);
      if (next instanceof PropagationFields) return (PropagationFields) next;
    }
    return null;
  }
}
