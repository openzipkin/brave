/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.internal;

import brave.propagation.TraceContext;
import java.util.Map;

/**
 * We need to retain propagation state extracted from headers. However, we don't know the trace
 * identifiers, yet. In order to resolve this ordering concern, we create an object to hold extra
 * state, and defer associating it with a span ID (via {@link PropagationFieldsFactory#decorate(TraceContext)}.
 *
 * <p>Implementations of this type should use copy-on-write semantics to prevent changes in a child
 * context from affecting its parent.
 */
public abstract class PropagationFields {
  long traceId, spanId; // guarded by this

  /** Returns the value of the field with the specified key or null if not available */
  public abstract String get(String name);

  /** Replaces the value of the field with the specified key, ignoring if not a permitted field */
  public abstract void put(String name, String value);

  /** for each field in the input replace the value if the key doesn't already exist */
  abstract void putAllIfAbsent(PropagationFields parent);

  public abstract Map<String, String> toMap();

  /** Fields are extracted before a context is created. We need to lazy set the context */
  final boolean tryToClaim(long traceId, long spanId) {
    synchronized (this) {
      if (this.traceId == 0L) {
        this.traceId = traceId;
        this.spanId = spanId;
        return true;
      }
      return this.traceId == traceId
        && this.spanId == spanId;
    }
  }

  @Override public String toString() {
    return getClass().getSimpleName() + toMap();
  }

  /** Returns the value of the field with the specified key or null if not available */
  public static String get(TraceContext context, String name,
    Class<? extends PropagationFields> type) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    PropagationFields fields = context.findExtra(type);
    return fields != null ? fields.get(name) : null;
  }

  /** Replaces the value of the field with the specified key, ignoring if not a permitted field */
  public static void put(TraceContext context, String name, String value,
    Class<? extends PropagationFields> type) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    if (value == null) throw new NullPointerException("value == null");
    PropagationFields fields = context.findExtra(type);
    if (fields == null) return;
    fields.put(name, value);
  }
}
