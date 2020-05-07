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

import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

public final class TraceContextPropagation extends Propagation.Factory
    implements Propagation<String> {
  static final String TRACEPARENT = "traceparent", TRACESTATE = "tracestate";

  public static Propagation.Factory create() {
    return new Builder().build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    static final TracestateFormat THROWING_VALIDATOR = new TracestateFormat(true);
    String tracestateKey = "b3";

    /**
     * The key to use inside the {@code tracestate} value. Defaults to "b3".
     *
     * @throws IllegalArgumentException if the key doesn't conform to ABNF rules defined by the
     * <href="https://www.w3.org/TR/trace-context-1/#key">trace-context specification</href>.
     */
    public Builder tracestateKey(String key) {
      if (key == null) throw new NullPointerException("key == null");
      THROWING_VALIDATOR.validateKey(key, 0, key.length());
      this.tracestateKey = key;
      return this;
    }

    public Propagation.Factory build() {
      return new TraceContextPropagation(this);
    }

    Builder() {
    }
  }

  final String tracestateKey;
  final Tracestate.Factory tracestateFactory;
  final List<String> keys = Collections.unmodifiableList(asList(TRACEPARENT, TRACESTATE));

  TraceContextPropagation(Builder builder) {
    this.tracestateKey = builder.tracestateKey;
    this.tracestateFactory = Tracestate.newFactory(tracestateKey);
  }

  @Override public List<String> keys() {
    return keys;
  }

  @Override public boolean requires128BitTraceId() {
    return true;
  }

  @Override public TraceContext decorate(TraceContext context) {
    return tracestateFactory.decorate(context);
  }

  @Override public Propagation<String> get() {
    return this;
  }

  @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
    return StringPropagationAdapter.create(this, keyFactory);
  }

  @Override public <R> Injector<R> injector(Setter<R, String> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new TraceContextInjector<>(this, setter);
  }

  @Override public <R> Extractor<R> extractor(Getter<R, String> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new TraceContextExtractor<>(this, getter);
  }
}
