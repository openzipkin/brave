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
package brave.features.opentracing;

import brave.Span.Kind;
import brave.propagation.Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.RemoteSetter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class TextMapPropagation {
  static final Setter<TextMapInject, String> SETTER = new Setter<TextMapInject, String>() {
    @Override public void put(TextMapInject request, String key, String value) {
      request.put(key, value);
    }

    @Override public String toString() {
      return "TextMapInject::put";
    }
  };

  enum REMOTE_SETTER implements RemoteSetter<TextMapInject> {
    CLIENT() {
      @Override public Kind spanKind() {
        return Kind.CLIENT;
      }
    },
    PRODUCER() {
      @Override public Kind spanKind() {
        return Kind.PRODUCER;
      }
    },
    CONSUMER() {
      @Override public Kind spanKind() {
        return Kind.CONSUMER;
      }
    };

    @Override public void put(TextMapInject request, String key, String value) {
      SETTER.put(request, key, value);
    }

    @Override public String toString() {
      return SETTER.toString();
    }
  }

  static final Getter<Map<String, String>, String> GETTER =
      new Getter<Map<String, String>, String>() {
        @Override public String get(Map<String, String> carrier, String key) {
          return carrier.get(key.toLowerCase(Locale.ROOT));
        }

        @Override public String toString() {
          return "Map::getLowerCase";
        }
      };

  /**
   * Even though TextMap is named like Map, it doesn't have a retrieve-by-key method.
   *
   * <p>See https://github.com/opentracing/opentracing-java/issues/305
   */
  static final class TextMapExtractor implements Extractor<TextMapExtract> {
    final Set<String> allNames;
    final Extractor<Map<String, String>> delegate;

    TextMapExtractor(
        Propagation<String> propagation,
        Set<String> allNames,
        Getter<Map<String, String>, String> getter) {
      this.allNames = allNames;
      this.delegate = propagation.extractor(getter);
    }

    /** Performs case-insensitive lookup */
    @Override public TraceContextOrSamplingFlags extract(TextMapExtract entries) {
      Map<String, String> cache = new LinkedHashMap<>();
      for (Iterator<Map.Entry<String, String>> it = entries.iterator(); it.hasNext(); ) {
        Map.Entry<String, String> next = it.next();
        String inputKey = next.getKey().toLowerCase(Locale.ROOT);
        if (allNames.contains(inputKey)) {
          cache.put(inputKey, next.getValue());
        }
      }
      return delegate.extract(cache);
    }
  }
}
