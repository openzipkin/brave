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
package brave.internal.codec;

import brave.internal.codec.EntrySplitter.Handler;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class EntrySplitterTest {
  EntrySplitter entrySplitter = EntrySplitter.newBuilder().shouldThrow(true).build();
  Map<String, String> map = new LinkedHashMap<>();
  Handler<Map<String, String>> parseIntoMap =
      (target, input, beginKey, endKey, beginValue, endValue) -> {
        String key = input.substring(beginKey, endKey);
        String value = input.substring(beginValue, endValue);
        target.put(key, value);
        return true;
      };

  @Test public void parse() {
    entrySplitter.parse(parseIntoMap, map, "k1=v1,k2=v2");

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "v2")
    );
  }

  @Test public void parse_singleChars() {
    entrySplitter.parse(parseIntoMap, map, "k=v,a=b");

    assertThat(map).containsExactly(
        entry("k", "v"),
        entry("a", "b")
    );
  }

  @Test public void parse_valuesAreRequired() {
    for (String missingValue : Arrays.asList("k1", "k1  ", "k1=v1,k2", "k1   ,k2=v1")) {
      assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, map, missingValue))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid input: missing key value separator '='");
    }
    assertThat(map.isEmpty());
  }

  @Test public void parse_emptyValuesOk() {
    for (String emptyValue : Arrays.asList("k1=", "k1 =", ",k1=", ",k1 =", "k1 =,")) {
      entrySplitter.parse(parseIntoMap, map, emptyValue);

      assertThat(map).containsExactly(entry("k1", ""));
      map.clear();
    }

    entrySplitter.parse(parseIntoMap, map, "k1=v1,k2=");

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "")
    );
  }

  @Test public void keyValueSeparatorRequired_false() {
    entrySplitter = EntrySplitter.newBuilder()
        .keyValueSeparatorRequired(false)
        .shouldThrow(true)
        .build();

    entrySplitter.parse(parseIntoMap, map, " authcache , gateway ");

    assertThat(map).containsExactly(
        entry("authcache", ""),
        entry("gateway", "")
    );
  }

  /** Parse Accept header style encoding as used in secondary sampling */
  @Test public void parse_onlyFirstKeyValueSeparator() {
    entrySplitter = EntrySplitter.newBuilder()
        .keyValueSeparator(';')
        .keyValueSeparatorRequired(false)
        .shouldThrow(true)
        .build();

    entrySplitter.parse(parseIntoMap, map, "authcache;ttl=1;spanId=19f84f102048e047,gateway");

    assertThat(map).containsExactly(
        entry("authcache", "ttl=1;spanId=19f84f102048e047"),
        entry("gateway", "")
    );
  }

  /** This shows you can nest parsers without unnecessary string allocation between stages. */
  @Test public void parse_nested() {
    EntrySplitter outerSplitter = EntrySplitter.newBuilder()
        .keyValueSeparator(';')
        .keyValueSeparatorRequired(false)
        .shouldThrow(true)
        .build();

    EntrySplitter innerSplitter = EntrySplitter.newBuilder()
        .entrySeparator(';')
        .keyValueSeparator('=')
        .shouldThrow(true)
        .build();

    Map<String, Map<String, String>> keyToAttributes = new LinkedHashMap<>();

    outerSplitter.parse((target, input, beginKey, endKey, beginValue, endValue) -> {
      String key = input.substring(beginKey, endKey);
      Map<String, String> attributes = new LinkedHashMap<>();
      if (beginValue == endValue) { // no string allocation at all
        attributes = Collections.emptyMap();
      } else { // no string allocation to pass to the inner parser
        attributes = new LinkedHashMap<>();
        innerSplitter.parse(parseIntoMap, attributes, input, beginValue, endValue);
      }
      target.put(key, attributes);
      return true;
    }, keyToAttributes, "authcache;ttl=1;spanId=19f84f102048e047,gateway");

    Map<String, String> expectedAttributes = new LinkedHashMap<>();
    expectedAttributes.put("ttl", "1");
    expectedAttributes.put("spanId", "19f84f102048e047");
    assertThat(keyToAttributes).containsExactly(
        entry("authcache", expectedAttributes),
        entry("gateway", Collections.emptyMap())
    );
  }

  @Test public void parse_emptyKeysNotOk() {
    for (String missingKey : Arrays.asList("=", "=v1", ",=", ",=v2")) {
      assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, map, missingKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid input: no key before '='");
    }
  }

  @Test public void parse_breaksWhenHandlerDoes() {
    entrySplitter = EntrySplitter.newBuilder().maxEntries(2).shouldThrow(true).build();

    entrySplitter.parse((target, input, beginKey, endKey, beginValue, endValue) -> {
      String key = input.substring(beginKey, endKey);
      if (key.equals("k1")) {
        target.put(key, input.substring(beginValue, endValue));
        return true;
      }
      return false;
    }, map, "k1=v1,k2=v2");

    assertThat(map).containsExactly(entry("k1", "v1"));
  }

  @Test public void parse_maxEntries() {
    entrySplitter = EntrySplitter.newBuilder().maxEntries(2).shouldThrow(true).build();

    entrySplitter.parse(parseIntoMap, map, "k1=v1,k2=v2");

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "v2")
    );

    assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, map, "k1=v1,k2=v2,k3=v3"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid input: over 2 entries");
  }

  @Test public void parse_whitespaceInKeyValue() {
    entrySplitter.parse(parseIntoMap, map, "k 1=v 1,k 2=v 2");

    assertThat(map).containsExactly(
        entry("k 1", "v 1"),
        entry("k 2", "v 2")
    );
  }

  @Test public void trimOWSAroundEntrySeparator() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(true)
        .trimOWSAroundKeyValueSeparator(false)
        .shouldThrow(true).build();

    entrySplitter.parse(parseIntoMap, map, "  k1   =   v1  ,  k2   =   v2  ");

    assertThat(map).containsExactly(
        entry("k1   ", "   v1"),
        entry("k2   ", "   v2")
    );
  }

  @Test public void trimOWSAroundKeyValueSeparator() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(false)
        .trimOWSAroundKeyValueSeparator(true)
        .shouldThrow(true).build();

    entrySplitter.parse(parseIntoMap, map, "  k1   =   v1  ,  k2   =   v2  ");

    assertThat(map).containsExactly(
        entry("  k1", "v1  "),
        entry("  k2", "v2  ")
    );
  }

  @Test public void trimOWSAroundSeparators() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(true)
        .trimOWSAroundKeyValueSeparator(true)
        .shouldThrow(true).build();

    entrySplitter.parse(parseIntoMap, map, "  k1   =   v1  ,  k2   =   v2  ");

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "v2")
    );
  }

  @Test public void trimOWSAroundNothing() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(false)
        .trimOWSAroundKeyValueSeparator(false)
        .shouldThrow(true).build();

    entrySplitter.parse(parseIntoMap, map, "  k1   =   v1  ,  k2   =   v2  ");

    assertThat(map).containsExactly(
        entry("  k1   ", "   v1  "),
        entry("  k2   ", "   v2  ")
    );
  }

  @Test public void toleratesButIgnores_empty() {
    entrySplitter.parse(parseIntoMap, map, "");

    assertThat(map.isEmpty());
  }

  @Test public void toleratesButIgnores_onlyWhitespace() {
    for (String w : Arrays.asList(" ", "\t")) {
      entrySplitter.parse(parseIntoMap, map, w);
      entrySplitter.parse(parseIntoMap, map, w + w);
    }

    assertThat(map.isEmpty());
  }

  @Test public void toleratesButIgnores_emptyMembers() {
    for (String w : Arrays.asList(" ", "\t")) {
      entrySplitter.parse(parseIntoMap, map, ",");
      entrySplitter.parse(parseIntoMap, map, w + ",");
      entrySplitter.parse(parseIntoMap, map, "," + w);
      entrySplitter.parse(parseIntoMap, map, ",,");
      entrySplitter.parse(parseIntoMap, map, "," + w + ",");
      entrySplitter.parse(parseIntoMap, map, w + "," + w + "," + w);
    }

    assertThat(map.isEmpty());
  }
}
