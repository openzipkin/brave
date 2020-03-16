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
  // TODO: not sure if we will want a constant here, or something like a builder. For example, we
  // probably will need a primary state handler (ex b3) to catch data no longer in the traceparent
  // format. At any rate, we will need to know what state is primarily ours, so that probably means
  // not having a constant, unless that constant uses b3 single impl for the tracestate entry.
  public static final Propagation.Factory FACTORY =
    new Propagation.Factory() {
      @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
        return new TraceContextPropagation<>(keyFactory);
      }

      /**
       * Traceparent doesn't support sharing the same span ID, though it may be possible to support
       * this by extending it with a b3 state entry.
       */
      @Override public boolean supportsJoin() {
        return false;
      }

      @Override public TraceContext decorate(TraceContext context) {
        // TODO: almost certain we will need to decorate as not all contexts will start with an
        // incoming request (ex schedule or client-originated traces)
        return super.decorate(context);
      }

      @Override public boolean requires128BitTraceId() {
        return true;
      }

      @Override public String toString() {
        return "TracestatePropagationFactory";
      }
    };

  final String stateName;
  final K traceparentKey, tracestateKey;
  final List<K> fields;

  TraceContextPropagation(KeyFactory<K> keyFactory) {
    this.stateName = "b3";
    this.traceparentKey = keyFactory.create("traceparent");
    this.tracestateKey = keyFactory.create("tracestate");
    this.fields = Arrays.asList(traceparentKey, tracestateKey);
  }

  @Override public List<K> keys() {
    return fields;
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
