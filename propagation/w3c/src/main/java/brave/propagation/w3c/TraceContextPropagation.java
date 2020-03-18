/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.propagation.w3c;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Arrays;
import java.util.List;

public final class TraceContextPropagation<K> implements Propagation<K> {

  public static Factory newFactory() {
    return new FactoryBuilder().build();
  }

  public static FactoryBuilder newFactoryBuilder() {
    return new FactoryBuilder();
  }

  public static final class FactoryBuilder {
    String tracestateKey = "b3";

    /**
     * The key to use inside the {@code tracestate} value. Defaults to "b3".
     *
     * @throws IllegalArgumentException if the key doesn't conform to ABNF rules defined by the
     * <href="https://www.w3.org/TR/trace-context-1/#key">trace-context specification</href>.
     */
    public FactoryBuilder tracestateKey(String key) {
      if (key == null) throw new NullPointerException("key == null");
      TracestateFormat.validateKey(key, true);
      this.tracestateKey = key;
      return this;
    }

    public Factory build() {
      return new Factory(this);
    }

    FactoryBuilder() {
    }
  }

  static final class Factory extends Propagation.Factory {
    final String tracestateKey;

    Factory(FactoryBuilder builder) {
      this.tracestateKey = builder.tracestateKey;
    }

    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new TraceContextPropagation<>(keyFactory, tracestateKey);
    }

    @Override public TraceContext decorate(TraceContext context) {
      // TODO: almost certain we will need to decorate as not all contexts will start with an
      // incoming request (ex schedule or client-originated traces)
      return super.decorate(context);
    }
  }

  final String tracestateKey;
  final K traceparent, tracestate;
  final List<K> keys;

  TraceContextPropagation(KeyFactory<K> keyFactory, String tracestateKey) {
    this.tracestateKey = tracestateKey;
    this.traceparent = keyFactory.create("traceparent");
    this.tracestate = keyFactory.create("tracestate");
    this.keys = Arrays.asList(traceparent, tracestate);
  }

  @Override public List<K> keys() {
    return keys;
  }

  @Override public <C> Injector<C> injector(Setter<C, K> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new TraceContextInjector<>(this, setter);
  }

  @Override public <C> Extractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new TraceContextExtractor<>(this, getter);
  }

  /**
   * This only contains other entries. The entry for the current trace is only written during
   * injection.
   */
  static final class Extra { // hidden intentionally
    CharSequence otherEntries;

    @Override public String toString() {
      return "TracestatePropagation{"
        + (otherEntries != null ? ("entries=" + otherEntries.toString()) : "")
        + "}";
    }
  }
}
