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
import brave.propagation.TraceContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.internal.codec.CharSequences.regionMatches;
import static brave.internal.codec.HexCodec.lenientLowerHexToUnsignedLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class EntrySplitterTest {
  EntrySplitter entrySplitter = EntrySplitter.newBuilder().shouldThrow(true).build();
  Map<String, String> map = new LinkedHashMap<>();
  Handler<Map<String, String>> parseIntoMap =
    (target, input, beginKey, endKey, beginValue, endValue) -> {
      String key = input.subSequence(beginKey, endKey).toString();
      String value = input.subSequence(beginValue, endValue).toString();
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
      String key = input.subSequence(beginKey, endKey).toString();
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

  /**
   * This is an example of how to parse without allocating strings. This is based on
   * https://github.com/openzipkin/zipkin-aws/blob/master/brave-propagation-aws/src/main/java/brave/propagation/aws/AWSPropagation.java
   */
  @Test public void example_parseAWSTraceId() {
    entrySplitter = EntrySplitter.newBuilder().entrySeparator(';').build();

    String awsTraceId =
      "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1";

    Handler<TraceContext.Builder> parseIntoMap =
      (target, input, beginKey, endKey, beginValue, endValue) -> {
        if (regionMatches("Root", input, beginKey, endKey)) {
          int valueLength = endValue - beginValue;
          int i = beginValue;
          if (valueLength != 35 // length of 1-67891233-abcdef012345678912345678
            || input.charAt(i++) != '1'
            || input.charAt(i++) != '-') {
            return false; // invalid version or format
          }
          long high32 = lenientLowerHexToUnsignedLong(input, i, i + 8);
          i += 9; // skip the hyphen
          long low32 = lenientLowerHexToUnsignedLong(input, i, i + 8);
          i += 8;
          long traceIdHigh = high32 << 32;
          traceIdHigh = traceIdHigh | low32;
          long traceId = lenientLowerHexToUnsignedLong(input, i, i + 16);
          if (traceIdHigh == 0L || traceId == 0L) return false;
          target.traceIdHigh(traceIdHigh).traceId(traceId);
          return true;
        } else if (regionMatches("Parent", input, beginKey, endKey)) {
          long spanId = lenientLowerHexToUnsignedLong(input, beginValue, endValue);
          if (spanId == 0L) return false;
          target.spanId(spanId);
          return true;
        } else if (regionMatches("Sampled", input, beginKey, endKey)) {
          switch (input.charAt(beginValue)) {
            case '0':
              target.sampled(false);
              break;
            case '1':
              target.sampled(true);
              break;
            default:
              return false;
          }
        }
        return true;
      };

    TraceContext.Builder builder = TraceContext.newBuilder();
    assertThat(entrySplitter.parse(parseIntoMap, builder, awsTraceId)).isTrue();
    TraceContext context = builder.build();

    assertThat(context).usingRecursiveComparison().isEqualTo(
      TraceContext.newBuilder()
        .traceIdHigh(0x67891233abcdef01L).traceId(0x2345678912345678L)
        .spanId(0x463ac35c9f6413adL)
        .sampled(true)
        .build()
    );
  }

  @Test public void parse_breaksWhenHandlerDoes() {
    entrySplitter = EntrySplitter.newBuilder().maxEntries(2).shouldThrow(true).build();

    entrySplitter.parse((target, input, beginKey, endKey, beginValue, endValue) -> {
      if (regionMatches("k1", input, beginKey, endKey)) {
        target.put("k1", input.subSequence(beginValue, endValue).toString());
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

  @Test public void builder_illegal() {
    EntrySplitter.Builder builder = EntrySplitter.newBuilder();

    assertThatThrownBy(() -> builder.maxEntries(-1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("maxEntries <= 0");
    assertThatThrownBy(() -> builder.maxEntries(0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("maxEntries <= 0");

    assertThatThrownBy(() -> builder.entrySeparator((char) 0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("entrySeparator == 0");

    assertThatThrownBy(() -> builder.keyValueSeparator((char) 0))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("keyValueSeparator == 0");

    builder.keyValueSeparator(';').entrySeparator(';');
    assertThatThrownBy(builder::build)
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("entrySeparator == keyValueSeparator");
  }

  @Test public void parse_badParameters() {
    assertThatThrownBy(() -> entrySplitter.parse(null, map, ""))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("handler == null");
    assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, null, ""))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("target == null");
    assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, map, null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("input == null");

    assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, map, "", -1, 1))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("beginIndex < 0");

    assertThatThrownBy(() -> entrySplitter.parse(parseIntoMap, map, "", 0, 2))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("endIndex > input.length()");
  }
}
