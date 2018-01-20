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
 * requestId = ExtraFieldPropagation.get("x-vcap-request-id");
 *
 * // You can also set or override the value similarly, which might be needed if a new request
 * ExtraFieldPropagation.get("x-country-code", "FO");
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
 *
 * <p>You can also prefix fields, if they follow a common pattern. For example, the following will
 * propagate the field "x-vcap-request-id" as-is, but send the fields "country-code" and "user-id"
 * on the wire as "baggage-country-code" and "baggage-user-id" respectively.
 *
 * <pre>{@code
 * // Setup your tracing instance with allowed fields
 * tracingBuilder.propagationFactory(
 *   ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                        .addField("x-vcap-request-id")
 *                        .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
 *                        .build()
 * );
 *
 * // Later, you can call below to affect the country code of the current trace context
 * ExtraFieldPropagation.set("country-code", "FO");
 * String countryCode = ExtraFieldPropagation.get("country-code");
 *
 * // Or, if you have a reference to a trace context, use it explicitly
 * ExtraFieldPropagation.set(span.context(), "country-code", "FO");
 * String countryCode = ExtraFieldPropagation.get(span.context(), "country-code");
 * }</pre>
 */
public final class ExtraFieldPropagation<K> implements Propagation<K> {
  /** Wraps an underlying propagation implementation, pushing one or more fields */
  public static Propagation.Factory newFactory(Propagation.Factory delegate, String... fieldNames) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (fieldNames == null) throw new NullPointerException("fieldNames == null");
    String[] validated = ensureLowerCase(Arrays.asList(fieldNames));
    return new Factory(delegate, validated, validated);
  }

  /** Wraps an underlying propagation implementation, pushing one or more fields */
  public static Propagation.Factory newFactory(Propagation.Factory delegate,
      Collection<String> fieldNames) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (fieldNames == null) throw new NullPointerException("fieldNames == null");
    String[] validated = ensureLowerCase(fieldNames);
    return new Factory(delegate, validated, validated);
  }

  public static FactoryBuilder newFactoryBuilder(Propagation.Factory delegate) {
    return new FactoryBuilder(delegate);
  }

  public static final class FactoryBuilder {
    final Propagation.Factory delegate;
    List<String> fieldNames = new ArrayList<>();
    Map<String, String[]> prefixedNames = new LinkedHashMap<>();

    FactoryBuilder(Propagation.Factory delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
    }

    /**
     * Adds a field that is referenced the same in-process as it is on the wire. For example, the
     * name "x-vcap-request-id" would be set as-is including the prefix.
     *
     * <p>Note: {@code fieldName} will be implicitly lower-cased.
     */
    public FactoryBuilder addField(String fieldName) {
      if (fieldName == null) throw new NullPointerException("fieldName == null");
      fieldName = fieldName.trim();
      if (fieldName.isEmpty()) throw new IllegalArgumentException("fieldName is empty");
      fieldNames.add(fieldName.toLowerCase(Locale.ROOT));
      return this;
    }

    /**
     * Adds a prefix when fields are extracted or injected from headers. For example, if the prefix
     * is "baggage-", the field "country-code" would end up as "baggage-country-code" on the wire.
     *
     * <p>Note: any {@code fieldNames} will be implicitly lower-cased.
     */
    public FactoryBuilder addPrefixedFields(String prefix, Collection<String> fieldNames) {
      if (prefix == null) throw new NullPointerException("prefix == null");
      if (prefix.isEmpty()) throw new IllegalArgumentException("prefix is empty");
      if (fieldNames == null) throw new NullPointerException("fieldNames == null");
      prefixedNames.put(prefix, ensureLowerCase(fieldNames));
      return this;
    }

    public Factory build() {
      if (prefixedNames.isEmpty()) {
        String[] validated = ensureLowerCase(fieldNames);
        return new Factory(delegate, validated, validated);
      }
      List<String> fields = new ArrayList<>(), keys = new ArrayList<>();
      if (!fieldNames.isEmpty()) {
        List<String> validated = Arrays.asList(ensureLowerCase(fieldNames));
        fields.addAll(validated);
        keys.addAll(validated);
      }
      for (Map.Entry<String, String[]> entry : prefixedNames.entrySet()) {
        String nextPrefix = entry.getKey();
        String[] nextFieldNames = entry.getValue();
        for (String nextFieldName : nextFieldNames) {
          fields.add(nextFieldName);
          keys.add(nextPrefix + nextFieldName);
        }
      }
      return new Factory(delegate, fields.toArray(new String[0]), keys.toArray(new String[0]));
    }
  }

  /** Synonym for {@link #get(String)} */
  @Nullable public static String current(String name) {
    return get(name);
  }

  /**
   * Returns the value of the field with the specified key or null if not available.
   *
   * <p>Prefer {@link #get(TraceContext, String)} if you have a reference to a span.
   */
  @Nullable public static String get(String name) {
    TraceContext context = currentTraceContext();
    return context != null ? get(context, name) : null;
  }

  /**
   * Sets the current value of the field with the specified key.
   *
   * <p>Prefer {@link #set(TraceContext, String, String)} if you have a reference to a span.
   */
   public static void set(String name, String value) {
    TraceContext context = currentTraceContext();
    if (context != null) set(context, name, value);
  }

  /**
   * Returns a mapping of fields in the current trace context, or empty if there are none.
   *
   * <p>Prefer {@link #set(TraceContext, String, String)} if you have a reference to a span.
   */
  public static Map<String, String> getAll() {
    TraceContext context = currentTraceContext();
    if (context == null) return Collections.emptyMap();
    return getAll(context);
  }

  /** Returns a mapping of fields in the given trace context, or empty if there are none. */
  public static Map<String, String> getAll(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    Extra extra = findExtra(context.extra());
    if (extra == null) return Collections.emptyMap();
    String[] elements = extra.values;
    if (elements == null) return Collections.emptyMap();

    Map<String, String> result = new LinkedHashMap<>();
    for (int i = 0, length = elements.length; i<length; i++) {
      String value = elements[i];
      if (value != null) result.put(extra.fieldNames[i], value);
    }
    return result;
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

  /** Sets the value of the field with the specified key */
  public static void set(TraceContext context, String name, String value) {
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
    final String[] fieldNames;
    final String[] keyNames;

    Factory(Propagation.Factory delegate, String[] fieldNames, String[] keyNames) {
      this.delegate = delegate;
      this.fieldNames = fieldNames;
      this.keyNames = keyNames;
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }

    @Override public final <K> Propagation<K> create(Propagation.KeyFactory<K> keyFactory) {
      int length = fieldNames.length;
      List<K> keys = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        keys.add(keyFactory.create(keyNames[i]));
      }
      return new ExtraFieldPropagation<>(delegate.create(keyFactory), fieldNames, keys);
    }

    @Override public TraceContext decorate(TraceContext context) {
      TraceContext result = delegate.decorate(context);
      int extraIndex = indexOfExtra(result.extra());
      if (extraIndex != -1) {
        Extra extra = (Extra) result.extra().get(extraIndex);
        if (!Arrays.equals(extra.fieldNames, fieldNames)) {
          throw new IllegalStateException(
              String.format("Mixed name configuration unsupported: found %s, expected %s",
                  Arrays.asList(extra.fieldNames), Arrays.asList(fieldNames))
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
        extra = new Extra(fieldNames);
        copyOfExtra.add(extra);
      }
      extra.context = context;
      return result.toBuilder().extra(Collections.unmodifiableList(copyOfExtra)).build();
    }
  }

  final Propagation<K> delegate;
  final String[] fieldNames;
  final List<K> keys, allKeys;

  ExtraFieldPropagation(Propagation<K> delegate, String[] fieldNames, List<K> keys) {
    this.delegate = delegate;
    this.fieldNames = fieldNames;
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
    final String[] fieldNames;
    volatile String[] values; // guarded by this, copy on write
    TraceContext context; // guarded by this

    Extra(String[] fieldNames) {
      this.fieldNames = fieldNames;
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
      for (int i = 0, length = fieldNames.length; i < length; i++) {
        if (fieldNames[i].equals(name)) return i;
      }
      return -1;
    }

    void set(int index, String value) {
      synchronized (this) {
        String[] elements = values;
        if (elements == null) {
          elements = new String[fieldNames.length];
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
      for (int i = 0, length = fieldNames.length; i < length; i++) {
        String maybeValue = elements[i];
        if (maybeValue == null) continue;
        contents.put(fieldNames[i], maybeValue);
      }
      return "ExtraFieldPropagation" + contents;
    }

    @Override public Extra clone() {
      Extra result = new Extra(fieldNames);
      result.values = values;
      return result;
    }
  }

  static final class ExtraFieldInjector<C, K> implements Injector<C> {
    final Injector<C> delegate;
    final Propagation.Setter<C, K> setter;
    final String[] fieldNames;
    final List<K> keys;

    ExtraFieldInjector(ExtraFieldPropagation<K> propagation, Setter<C, K> setter) {
      this.delegate = propagation.delegate.injector(setter);
      this.fieldNames = propagation.fieldNames;
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
      Extra extra = new Extra(propagation.fieldNames);
      for (int i = 0, length = propagation.fieldNames.length; i < length; i++) {
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
