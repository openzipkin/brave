package brave.propagation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Implements the propagation format described in {@link B3SingleFormat}. */
public final class B3SinglePropagation<K> implements Propagation<K> {

  public static final Factory FACTORY = new Factory() {
    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new B3SinglePropagation<>(keyFactory);
    }

    @Override public boolean supportsJoin() {
      return true;
    }

    @Override public String toString() {
      return "B3SinglePropagationFactory";
    }
  };

  final K b3Key;
  final List<K> fields;

  B3SinglePropagation(KeyFactory<K> keyFactory) {
    this.b3Key = keyFactory.create("b3");
    this.fields = Collections.unmodifiableList(Arrays.asList(b3Key));
  }

  @Override public List<K> keys() {
    return fields;
  }

  @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new B3SingleInjector<>(this, setter);
  }

  static final class B3SingleInjector<C, K> implements TraceContext.Injector<C> {
    final B3SinglePropagation<K> propagation;
    final Setter<C, K> setter;

    B3SingleInjector(B3SinglePropagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      setter.put(carrier, propagation.b3Key, B3SingleFormat.writeB3SingleFormat(traceContext));
    }
  }

  @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new B3SingleExtractor<>(b3Key, getter);
  }

  static final class B3SingleExtractor<C, K> implements TraceContext.Extractor<C> {
    final K b3Key;
    final Getter<C, K> getter;

    B3SingleExtractor(K b3Key, Getter<C, K> getter) {
      this.b3Key = b3Key;
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      if (carrier == null) throw new NullPointerException("carrier == null");
      String b3 = getter.get(carrier, b3Key);
      if (b3 == null) return TraceContextOrSamplingFlags.EMPTY;

      TraceContextOrSamplingFlags extracted = B3SingleFormat.parseB3SingleFormat(b3);
      // if null, the trace context is malformed so return empty
      if (extracted == null) return TraceContextOrSamplingFlags.EMPTY;
      return extracted;
    }
  }
}
