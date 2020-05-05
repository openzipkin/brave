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
package brave.features.propagation;

import brave.internal.propagation.StringPropagationAdapter;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is an example where a load balancer is sending a B3 compatible trace ID, but using a
 * different header name. In ideal case, the load balancer just switches to use B3 single format.
 * This is an example of a hack you can do when it can't.
 *
 * <p>See https://github.com/openzipkin/b3-propagation
 */
public final class CustomTraceIdPropagation extends Propagation.Factory
    implements Propagation<String> {
  static final String B3 = "b3", TRACE_ID = "X-B3-TraceId";

  /**
   * @param delegate some configuration of {@link B3Propagation#newFactoryBuilder()}
   * @param customTraceIdName something not "b3"
   */
  public static Propagation.Factory create(Propagation.Factory delegate, String customTraceIdName) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (customTraceIdName == null) throw new NullPointerException("customTraceIdName == null");
    if (!delegate.create(KeyFactory.STRING).keys().contains("b3")) {
      throw new IllegalArgumentException("delegate must implement B3 propagation");
    }
    return new CustomTraceIdPropagation(delegate, customTraceIdName);
  }

  final Propagation.Factory factory;
  final Propagation<String> delegate;
  final String customTraceIdName;
  final List<String> allKeys;

  CustomTraceIdPropagation(Propagation.Factory factory, String customTraceIdName) {
    this.factory = factory;
    this.delegate = factory.get();
    this.customTraceIdName = customTraceIdName;
    //this.b3Key = keyFactory.create("b3", "X-B3-TraceId");
    List<String> allKeys = new ArrayList<>(delegate.keys());
    allKeys.add(customTraceIdName);
    this.allKeys = Collections.unmodifiableList(allKeys);
  }

  @Override public boolean supportsJoin() {
    return factory.supportsJoin();
  }

  @Override public boolean requires128BitTraceId() {
    return factory.requires128BitTraceId();
  }

  @Override public TraceContext decorate(TraceContext context) {
    return factory.decorate(context);
  }

  @Override public Propagation<String> get() {
    return this;
  }

  @Override public <K1> Propagation<K1> create(KeyFactory<K1> keyFactory) {
    return StringPropagationAdapter.create(this, keyFactory);
  }

  @Override public List<String> keys() {
    return allKeys;
  }

  @Override public <R> Extractor<R> extractor(Propagation.Getter<R, String> getter) {
    return delegate.extractor(new Getter<>(getter, customTraceIdName));
  }

  @Override public <R> Injector<R> injector(Propagation.Setter<R, String> setter) {
    return delegate.injector(setter);
  }

  static final class Getter<R> implements Propagation.Getter<R, String> {
    final Propagation.Getter<R, String> delegate;
    final String customTraceIdName;

    Getter(Propagation.Getter<R, String> delegate, String customTraceIdName) {
      this.delegate = delegate;
      this.customTraceIdName = customTraceIdName;
    }

    @Override public String get(R request, String key) {
      if (key.equals(B3)) {
        // Don't override a valid B3 context
        String b3 = delegate.get(request, key);
        if (b3 != null) return b3;
        if (delegate.get(request, TRACE_ID) != null) return null;

        // At this point, we know this is not a valid B3 context, check for a valid custom trace ID
        String customTraceId = delegate.get(request, customTraceIdName);
        if (customTraceId == null) return null;

        // We still expect the trace ID to be of valid length.
        int traceIdLength = customTraceId.length();
        if (traceIdLength < 16 || traceIdLength > 32) return null;

        // Synthesize a B3 single header out of the custom trace ID.
        // If invalid characters exist, the default parser will fail leniently.
        StringBuilder builder = new StringBuilder();
        for (int traceIdPad = 32 - traceIdLength; traceIdPad > 0; traceIdPad--) {
          builder.append('0');
        }
        builder.append(customTraceId).append('-');

        int spanIdIndex = traceIdLength > 16 ? traceIdLength - 16 : 0;
        builder.append(customTraceId.substring(spanIdIndex));
        return builder.toString();
      }
      return delegate.get(request, key);
    }
  }
}
