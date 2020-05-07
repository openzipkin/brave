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

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class EntrySplitterTest {
  EntrySplitter entrySplitter = EntrySplitter.newBuilder().build();
  Map<String, String> map = new LinkedHashMap<>();
  EntrySplitter.Handler<Map<String, String>> parseIntoMap =
      (target, buffer, beginKey, endKey, beginValue, endValue) -> {
        String key = buffer.substring(beginKey, endKey);
        String value = buffer.substring(beginValue, endValue);
        target.put(key, value);
        return true;
      };

  @Test public void parse() {
    entrySplitter.parse("k1=v1,k2=v2", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "v2")
    );
  }

  @Test public void parse_valuesAreRequired() {
    for (String missingValue : Arrays.asList("k1", "k1=v1,k2")) {
      assertThatThrownBy(() -> entrySplitter.parse(missingValue, parseIntoMap, map, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid input: missing key value separator '='");
    }
    assertThat(map.isEmpty());
  }

  @Test public void parse_emptyValuesOk() {
    for (String emptyValue : Arrays.asList("k1=", ",k1=")) {
      entrySplitter.parse(emptyValue, parseIntoMap, map, true);

      assertThat(map).containsExactly(entry("k1", ""));
      map.clear();
    }

    entrySplitter.parse("k1=v1,k2=", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "")
    );
  }

  @Test public void parse_emptyKeysNotOk() {
    for (String missingKey : Arrays.asList("=", "=v1", ",=", ",=v2")) {
      assertThatThrownBy(() -> entrySplitter.parse(missingKey, parseIntoMap, map, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid input: missing key before ','");
    }
  }

  @Test public void parse_breaksWhenHandlerDoes() {
    entrySplitter = EntrySplitter.newBuilder().maxEntries(2).build();

    entrySplitter.parse("k1=v1,k2=v2",
        (target, buffer, beginKey, endKey, beginValue, endValue) -> {
          String key = buffer.substring(beginKey, endKey);
          if (key.equals("k1")) {
            target.put(key, buffer.substring(beginValue, endValue));
            return true;
          }
          return false;
        }, map, true);

    assertThat(map).containsExactly(entry("k1", "v1"));
  }

  @Test public void parse_maxEntries() {
    entrySplitter = EntrySplitter.newBuilder().maxEntries(2).build();

    entrySplitter.parse("k1=v1,k2=v2", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "v2")
    );

    assertThatThrownBy(() -> entrySplitter.parse("k1=v1,k2=v2,k3=v3", parseIntoMap, map, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid input: over 2 entries");
  }

  @Test public void parse_whitespaceInKeyValue() {
    entrySplitter.parse("k 1=v 1,k 2=v 2", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("k 1", "v 1"),
        entry("k 2", "v 2")
    );
  }

  @Test public void trimOWSAroundEntrySeparator() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(true)
        .trimOWSAroundKeyValueSeparator(false).build();

    entrySplitter.parse("  k1   =   v1  ,  k2   =   v2  ", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("k1   ", "   v1"),
        entry("k2   ", "   v2")
    );
  }

  @Test public void trimOWSAroundKeyValueSeparator() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(false)
        .trimOWSAroundKeyValueSeparator(true).build();

    entrySplitter.parse("  k1   =   v1  ,  k2   =   v2  ", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("  k1", "v1  "),
        entry("  k2", "v2  ")
    );
  }

  @Test public void trimOWSAroundSeparators() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(true)
        .trimOWSAroundKeyValueSeparator(true).build();

    entrySplitter.parse("  k1   =   v1  ,  k2   =   v2  ", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("k1", "v1"),
        entry("k2", "v2")
    );
  }

  @Test public void trimOWSAroundNothing() {
    entrySplitter = EntrySplitter.newBuilder()
        .trimOWSAroundEntrySeparator(false)
        .trimOWSAroundKeyValueSeparator(false).build();

    entrySplitter.parse("  k1   =   v1  ,  k2   =   v2  ", parseIntoMap, map, true);

    assertThat(map).containsExactly(
        entry("  k1   ", "   v1  "),
        entry("  k2   ", "   v2  ")
    );
  }

  @Test public void toleratesButIgnores_empty() {
    entrySplitter.parse("", parseIntoMap, map, true);

    assertThat(map.isEmpty());
  }

  @Test public void toleratesButIgnores_onlyWhitespace() {
    for (String w : Arrays.asList(" ", "\t")) {
      entrySplitter.parse(w, parseIntoMap, map, true);
      entrySplitter.parse(w + w, parseIntoMap, map, true);
    }

    assertThat(map.isEmpty());
  }

  @Test public void toleratesButIgnores_emptyMembers() {
    for (String w : Arrays.asList(" ", "\t")) {
      entrySplitter.parse(",", parseIntoMap, map, true);
      entrySplitter.parse(w + ",", parseIntoMap, map, true);
      entrySplitter.parse("," + w, parseIntoMap, map, true);
      entrySplitter.parse(",,", parseIntoMap, map, true);
      entrySplitter.parse(w + "," + w + "," + w, parseIntoMap, map, true);
    }

    assertThat(map.isEmpty());
  }
}
