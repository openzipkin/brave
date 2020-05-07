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

import brave.internal.extra.MapExtra;
import brave.internal.extra.MapExtraFactory;

final class Tracestate extends MapExtra<String, String, Tracestate, Tracestate.Factory> {
  static Factory newFactory(String tracestateKey) {
    // max is total initial + dynamic
    return new FactoryBuilder().addInitialKey(tracestateKey).maxDynamicEntries(31).build();
  }

  static final class FactoryBuilder extends
      MapExtraFactory.Builder<String, String, Tracestate, Factory, FactoryBuilder> {
    @Override protected Factory build() {
      return new Factory(this);
    }
  }

  static final class Factory extends MapExtraFactory<String, String, Tracestate, Factory> {
    Factory(FactoryBuilder builder) {
      super(builder);
    }

    @Override protected Tracestate create() {
      return new Tracestate(this);
    }
  }

  Tracestate(Factory factory) {
    super(factory);
  }

  @Override protected String get(String key) {
    return super.get(key);
  }

  @Override protected String stateString() {
    Object[] array = (Object[]) state;
    // TODO: SHOULD on 512 char limit https://w3c.github.io/trace-context/#tracestate-limits
    StringBuilder result = new StringBuilder();
    boolean empty = true;
    for (int i = 0; i < array.length; i += 2) {
      String key = (String) array[i], value = (String) array[i + 1];
      if (value == null) continue;
      if (!empty) result.append(',');
      result.append(key).append('=').append(value);
      empty = false;
    }
    return result.toString();
  }

  @Override protected boolean put(String key, String value) {
    return super.put(key, value);
  }
}
