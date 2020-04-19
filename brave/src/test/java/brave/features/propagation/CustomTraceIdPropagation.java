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

import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
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
public final class CustomTraceIdPropagation<K> implements Propagation<K> {
  public static Propagation.Factory create(Propagation.Factory delegate, String customTraceIdName) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (customTraceIdName == null) throw new NullPointerException("customTraceIdName == null");
    if (!delegate.create(KeyFactory.STRING).keys().contains("b3")) {
      throw new IllegalArgumentException("delegate must implement B3 propagation");
    }
    return new Factory(delegate, customTraceIdName);
  }

  static final class Factory extends Propagation.Factory {
    final Propagation.Factory delegate;
    final String customTraceIdName;

    /**
     * @param delegate some configuration of {@link B3Propagation#newFactoryBuilder()}
     * @param customTraceIdName something not "b3"
     */
    Factory(Propagation.Factory delegate, String customTraceIdName) {
      this.delegate = delegate;
      this.customTraceIdName = customTraceIdName;
    }

    @Override public boolean supportsJoin() {
      return delegate.supportsJoin();
    }

    @Override public boolean requires128BitTraceId() {
      return delegate.requires128BitTraceId();
    }

    @Override public TraceContext decorate(TraceContext context) {
      return delegate.decorate(context);
    }

    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new CustomTraceIdPropagation<>(this, keyFactory);
    }
  }

  final Propagation<K> delegate;
  final K b3Key, b3TraceIdKey, customTraceIdKey;
  final List<K> allKeys;

  CustomTraceIdPropagation(Factory factory, KeyFactory<K> keyFactory) {
    this.delegate = factory.delegate.create(keyFactory);
    this.b3Key = keyFactory.create("b3");
    this.b3TraceIdKey = keyFactory.create("X-B3-TraceId");
    this.customTraceIdKey = keyFactory.create(factory.customTraceIdName);
    List<K> allKeys = new ArrayList<>(delegate.keys());
    allKeys.add(customTraceIdKey);
    this.allKeys = Collections.unmodifiableList(allKeys);
  }

  @Override public List<K> keys() {
    return allKeys;
  }

  @Override public <R> TraceContext.Extractor<R> extractor(Propagation.Getter<R, K> getter) {
    return delegate.extractor(new Getter<>(getter, b3Key, b3TraceIdKey, customTraceIdKey));
  }

  @Override public <R> TraceContext.Injector<R> injector(Propagation.Setter<R, K> setter) {
    return delegate.injector(setter);
  }

  static final class Getter<R, K> implements Propagation.Getter<R, K> {
    final Propagation.Getter<R, K> delegate;
    private final K b3Key, b3TraceIdKey, customTraceIdKey;

    Getter(Propagation.Getter<R, K> delegate, K b3Key, K b3TraceIdKey, K customTraceIdKey) {
      this.delegate = delegate;
      this.b3Key = b3Key;
      this.b3TraceIdKey = b3TraceIdKey;
      this.customTraceIdKey = customTraceIdKey;
    }

    @Override public String get(R request, K key) {
      if (key.equals(b3Key)) {
        // Don't override a valid B3 context
        String b3 = delegate.get(request, key);
        if (b3 != null) return b3;
        if (delegate.get(request, b3TraceIdKey) != null) return null;

        // At this point, we know this is not a valid B3 context, check for a valid custom trace ID
        String customTraceId = delegate.get(request, customTraceIdKey);
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
