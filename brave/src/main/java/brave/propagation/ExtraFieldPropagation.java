package brave.propagation;

import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Allows you to propagate predefined request-scoped fields, usually but not always HTTP headers.
 *
 * <p>For example, if you are in a Cloud Foundry environment, you might want to pass the request
 * ID:
 * <pre>{@code
 * // when you initialize the builder, define the extra field you want to propagate
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-vcap-request-id")
 * );
 *
 * // later, you can tag that request ID or use it in log correlation
 * requestId = ExtraFieldPropagation.current("x-vcap-request-id");
 *
 * // You can also set or override the value similarly, which might be needed if a new request
 * ExtraFieldPropagation.current("x-country-code", "FO");
 * }</pre>
 *
 * <p>You may also need to propagate a trace context you aren't using. For example, you may be in an
 * Amazon Web Services environment, but not reporting data to X-Ray. To ensure X-Ray can co-exist
 * correctly, pass-through its tracing header like so.
 *
 * <pre>{@code
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-amzn-trace-id")
 * );
 * }</pre>
 */
public final class ExtraFieldPropagation<K> implements Propagation<K> {
  /** Wraps an underlying propagation implementation, pushing one or more fields */
  public static Propagation.Factory newFactory(Propagation.Factory delegate, String... validNames) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (validNames == null) throw new NullPointerException("validNames == null");
    return new Factory(delegate, ensureLowerCase(Arrays.asList(validNames)));
  }

  /** Wraps an underlying propagation implementation, pushing one or more fields */
  public static Propagation.Factory newFactory(Propagation.Factory delegate,
      Collection<String> validNames) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (validNames == null) throw new NullPointerException("validNames == null");
    return new Factory(delegate, ensureLowerCase(validNames));
  }

  /** Returns the value of the field with the specified key or null if not available */
  @Nullable public static String current(String name) {
    TraceContext context = currentTraceContext();
    return context != null ? get(context, name) : null;
  }

  /** Sets the current value of the field with the specified key */
  @Nullable public static void current(String name, String value) {
    TraceContext context = currentTraceContext();
    if (context != null) set(context, name, value);
  }

  @Nullable static TraceContext currentTraceContext() {
    Tracing tracing = Tracing.current();
    return tracing != null ? tracing.currentTraceContext().get() : null;
  }

  /** Returns the value of the field with the specified key or null if not available */
  @Nullable public static String get(TraceContext context, String name) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    Extra extra = findExtra(context.extra());
    if (extra == null) return null;
    int index = extra.indexOf(name.toLowerCase(Locale.ROOT));
    return index != -1 ? extra.get(index) : null;
  }

  /** Returns the value of the field with the specified key or null if not available */
  @Nullable public static void set(TraceContext context, String name, String value) {
    if (context == null) throw new NullPointerException("context == null");
    if (name == null) throw new NullPointerException("name == null");
    if (value == null) throw new NullPointerException("value == null");
    Extra extra = findExtra(context.extra());
    if (extra == null) return;
    int index = extra.indexOf(name.toLowerCase(Locale.ROOT));
    extra.set(index, value);
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final String[] validNames;

    Factory(Propagation.Factory delegate, String[] validNames) {
      this.delegate = delegate;
      this.validNames = validNames;
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }

    @Override public final <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      int length = validNames.length;
      List<K> keys = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        keys.add(keyFactory.create(validNames[i]));
      }
      return new ExtraFieldPropagation<>(delegate.create(keyFactory), validNames, keys);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegate.decorate(context);
      int extraIndex = indexOfExtra(result.extra());
      if (extraIndex != -1) {
        Extra extra = (Extra) result.extra().get(extraIndex);
        if (!Arrays.equals(extra.validNames, validNames)) {
          throw new IllegalStateException(
              String.format("Mixed name configuration unsupported: found %s, expected %s",
                  Arrays.asList(extra.validNames), Arrays.asList(validNames))
          );
        }

        // If this extra is unassociated (due to remote extraction),
        // or it is the same span ID, re-use the instance.
        if (extra.tryAssociate(context)) return context;
      }
      // otherwise, we are creating a new instance of
      List<Object> copyOfExtra = new ArrayList<>(result.extra());
      Extra extra;
      if (extraIndex != -1) {
        extra = ((Extra) copyOfExtra.get(extraIndex)).clone();
        copyOfExtra.set(extraIndex, extra);
      } else {
        extra = new Extra(validNames);
        copyOfExtra.add(extra);
      }
      extra.context = context;
      return result.toBuilder().extra(Collections.unmodifiableList(copyOfExtra)).build();
    }
  }

  final Propagation<K> delegate;
  final String[] validNames;
  final List<K> keys, allKeys;

  ExtraFieldPropagation(Propagation<K> delegate, String[] validNames, List<K> keys) {
    this.delegate = delegate;
    this.validNames = validNames;
    this.keys = keys;
    List<K> allKeys = new ArrayList<>(delegate.keys());
    allKeys.addAll(keys);
    this.allKeys = allKeys;
  }

  @Override public List<K> keys() {
    return allKeys;
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    return new ExtraFieldInjector<>(this, setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    return new ExtraFieldExtractor<>(this, getter);
  }

  /** Copy-on-write keeps propagation changes in a child context from affecting its parent */
  static final class Extra implements Cloneable {
    final String[] validNames;
    volatile String[] values; // guarded by this, copy on write
    TraceContext context; // guarded by this

    Extra(String[] validNames) {
      this.validNames = validNames;
    }

    /** Extra data are extracted before a context is created. We need to lazy set the context */
    boolean tryAssociate(TraceContext newContext) {
      synchronized (this) {
        if (context == null) {
          context = newContext;
          return true;
        }
        return context.traceId() == newContext.traceId()
            && context.spanId() == newContext.spanId();
      }
    }

    int indexOf(String name) {
      for (int i = 0, length = validNames.length; i < length; i++) {
        if (validNames[i].equals(name)) return i;
      }
      return -1;
    }

    void set(int index, String value) {
      synchronized (this) {
        String[] elements = values;
        if (elements == null) {
          elements = new String[validNames.length];
          elements[index] = value;
        } else if (!value.equals(elements[index])) {
          // this is the copy-on-write part
          elements = Arrays.copyOf(elements, elements.length);
          elements[index] = value;
        }
        values = elements;
      }
    }

    String get(int index) {
      final String result;
      synchronized (this) {
        String[] elements = values;
        result = elements != null ? elements[index] : null;
      }
      return result;
    }

    @Override public String toString() {
      String[] elements;
      synchronized (this) {
        elements = values;
      }

      Map<String, String> contents = new LinkedHashMap<>();
      for (int i = 0, length = validNames.length; i < length; i++) {
        String maybeValue = elements[i];
        if (maybeValue == null) continue;
        contents.put(validNames[i], maybeValue);
      }
      return "ExtraFieldPropagation" + contents;
    }

    @Override public Extra clone() {
      Extra result = new Extra(validNames);
      result.values = values;
      return result;
    }
  }

  static final class ExtraFieldInjector<C, K> implements Injector<C> {
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;
    final String[] validNames;
    final List<K> keys;

    ExtraFieldInjector(ExtraFieldPropagation<K> propagation, Setter<C, K> setter) {
      this.delegate = propagation.delegate.injector(setter);
      this.validNames = propagation.validNames;
      this.keys = propagation.keys;
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      delegate.inject(traceContext, carrier);
      int extraIndex = indexOfExtra(traceContext.extra());
      if (extraIndex == -1) return;
      Extra extra = (Extra) traceContext.extra().get(extraIndex);
      for (int i = 0, length = keys.size(); i < length; i++) {
        String maybeValue = extra.get(i);
        if (maybeValue == null) continue;
        setter.put(carrier, keys.get(i), maybeValue);
      }
    }
  }

  static final class ExtraFieldExtractor<C, K> implements Extractor<C> {
    final ExtraFieldPropagation<K> propagation;
    final Extractor<C> delegate;
    final Propagation.Getter<C, K> getter;

    ExtraFieldExtractor(ExtraFieldPropagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.delegate = propagation.delegate.extractor(getter);
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      TraceContextOrSamplingFlags result = delegate.extract(carrier);

      // always allocate in case fields are added late
      Extra extra = new Extra(propagation.validNames);
      for (int i = 0, length = propagation.validNames.length; i < length; i++) {
        String maybeValue = getter.get(carrier, propagation.keys.get(i));
        if (maybeValue == null) continue;
        extra.set(i, maybeValue);
      }
      return result.toBuilder().addExtra(extra).build();
    }
  }

  static Extra findExtra(List<Object> extra) {
    int i = indexOfExtra(extra);
    return i != -1 ? (Extra) extra.get(i) : null;
  }

  static int indexOfExtra(List<Object> extra) {
    for (int i = 0, length = extra.size(); i < length; i++) {
      if (extra.get(i) instanceof Extra) return i;
    }
    return -1;
  }

  static String[] ensureLowerCase(Collection<String> names) {
    if (names.isEmpty()) throw new IllegalArgumentException("names is empty");
    Iterator<String> nextName = names.iterator();
    String[] result = new String[names.size()];
    for (int i = 0; nextName.hasNext(); i++) {
      String name = nextName.next();
      if (name == null) throw new NullPointerException("names[" + i + "] == null");
      name = name.trim();
      if (name.isEmpty()) throw new IllegalArgumentException("names[" + i + "] is empty");
      result[i] = name.toLowerCase(Locale.ROOT);
    }
    return result;
  }
}
