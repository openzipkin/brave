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
package brave.internal.propagation;

import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This supports the deprecated {@link Propagation.Factory#create(KeyFactory)}.
 *
 * <p>Here's how to integrate:
 *
 * <p>First, override {@link Propagation.Factory#get()} with your actual implementation. Then,
 * dispatch {@link Propagation.Factory#create(KeyFactory)} to your implementation via {@link
 * #create(Propagation, KeyFactory)}:
 *
 * <p><pre>{@code
 * @Override public Propagation<String> get() {
 *   return new YourPropagation(this);
 * }
 *
 * @Deprecated public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
 *   return StringPropagationAdapter.create(get(), keyFactory)
 * }
 * }</pre>
 *
 * <p><em>This is internal, and will be removed in Brave 6</em>. However, it won't be removed
 * before Brave 6. If you wish to use this, use "maven-shade-plugin" with the following
 * configuration, or something similar.
 *
 * <pre>{@code
 * <configuration>
 * <createDependencyReducedPom>false</createDependencyReducedPom>
 * <artifactSet>
 *   <includes>
 *     <include>io.zipkin.brave:brave</include>
 *   </includes>
 * </artifactSet>
 * <filters>
 *   <filter>
 *     <artifact>io.zipkin.brave:brave</artifact>
 *     <includes>
 *       <include>brave/internal/propagation/StringPropagationAdapter*.class</include>
 *     </includes>
 *   </filter>
 * </filters>
 * <relocations>
 *   <relocation>
 *     <pattern>brave.internal.propagation</pattern>
 *     <shadedPattern>YOUR_PACKAGE.brave_internal</shadedPattern>
 *   </relocation>
 * </relocations>
 * }</pre>
 *
 * @since 5.12
 */
public final class StringPropagationAdapter<K> implements Propagation<K> {
  public static <K> Propagation<K> create(Propagation<String> delegate, KeyFactory<K> keyFactory) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (keyFactory == null) throw new NullPointerException("keyFactory == null");
    if (keyFactory == KeyFactory.STRING) return (Propagation<K>) delegate;
    return new StringPropagationAdapter<>(delegate, keyFactory);
  }

  final Propagation<String> delegate;
  final KeyFactory<K> keyFactory;
  final Map<String, K> map;
  final List<K> keysList;

  StringPropagationAdapter(Propagation<String> delegate, KeyFactory<K> keyFactory) {
    this.delegate = delegate;
    this.keyFactory = keyFactory;
    this.map = new LinkedHashMap<>();
    this.keysList = toKeyList(delegate.keys(), keyFactory);
  }

  List<K> toKeyList(List<String> keyNames, KeyFactory<K> keyFactory) {
    int length = keyNames.size();
    K[] keys = (K[]) new Object[length];
    for (int i = 0; i < length; i++) {
      String keyName = keyNames.get(i);
      K key = keyFactory.create(keyName);
      keys[i] = key;
      map.put(keyName, key);
    }
    return Collections.unmodifiableList(Arrays.asList(keys));
  }

  @Override public List<K> keys() {
    return keysList;
  }

  @Override public <R> Injector<R> injector(Setter<R, K> setter) {
    return delegate.injector(new SetterAdapter<>(setter, map));
  }

  @Override public <R> Extractor<R> extractor(Getter<R, K> getter) {
    return delegate.extractor(new GetterAdapter<>(getter, map));
  }

  @Override public int hashCode() {
    return delegate.hashCode();
  }

  @Override public String toString() {
    return delegate.toString();
  }

  @Override public boolean equals(Object obj) {
    if (obj instanceof StringPropagationAdapter) {
      return delegate.equals(((StringPropagationAdapter) obj).delegate);
    } else if (obj instanceof Propagation) {
      return delegate.equals(obj);
    }
    return false;
  }

  static final class GetterAdapter<R, K> implements Getter<R, String> {
    final Getter<R, K> getter;
    final Map<String, K> map;

    GetterAdapter(Getter<R, K> getter, Map<String, K> map) {
      if (getter == null) throw new NullPointerException("getter == null");
      this.getter = getter;
      this.map = map;
    }

    @Override public String get(R request, String keyName) {
      K key = map.get(keyName);
      if (key == null) return null;
      return getter.get(request, key);
    }

    @Override public int hashCode() {
      return getter.hashCode();
    }

    @Override public String toString() {
      return getter.toString();
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof GetterAdapter) {
        return getter.equals(((GetterAdapter) obj).getter);
      } else if (obj instanceof Getter) {
        return getter.equals(obj);
      }
      return false;
    }
  }

  static final class SetterAdapter<R, K> implements Setter<R, String> {
    final Setter<R, K> setter;
    final Map<String, K> map;

    SetterAdapter(Setter<R, K> setter, Map<String, K> map) {
      if (setter == null) throw new NullPointerException("setter == null");
      this.setter = setter;
      this.map = map;
    }

    @Override public void put(R request, String keyName, String value) {
      K key = map.get(keyName);
      if (key == null) return;
      setter.put(request, key, value);
    }

    @Override public int hashCode() {
      return setter.hashCode();
    }

    @Override public String toString() {
      return setter.toString();
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof SetterAdapter) {
        return setter.equals(((SetterAdapter) obj).setter);
      } else if (obj instanceof Setter) {
        return setter.equals(obj);
      }
      return false;
    }
  }
}
