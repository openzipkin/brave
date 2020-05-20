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
import brave.propagation.TraceContext.Extractor;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This shows how people constrained to only propagating a trace ID can stash numerical data into
 * the trace ID. For example, a load balancer can set some bit flags like this.
 *
 * <p>Rumor has it that Twitter used to do this for embedding device information to ensure a
 * specific device couldn't absorb the entire random space. It isn't known if the amount of bits
 * stolen was a nibble or a byte.
 *
 * <p>See https://github.com/spring-cloud/spring-cloud-sleuth/issues/1106
 */
public class HackedTraceIdTest {
  String customTraceIdName = "trace_id";
  // CustomTraceIdPropagation.Factory substitutes for B3Propagation.FACTORY in real config.
  Propagation.Factory propagationFactory =
    CustomTraceIdPropagation.create(B3Propagation.FACTORY, customTraceIdName);
  Propagation<String> propagation = propagationFactory.get();
  Extractor<Map<String, String>> extractor = propagation.extractor(Map::get);
  Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

  // Let's say environment number zero is invalid, and its desired value is 3
  // There are at least 3 ways to embed this!
  @Test public void testFormatThatEmbedsEnvironmentNumber() {
    // Lead with single-digit, then pad-right zeros until the real trace ID.
    //
    // This is not great because it limits to 9 environment numbers. However, parsing is easy
    // as you look only at the first character.
    headers.put(customTraceIdName, "3000000000000000e457b5a2e4d86bd1");
    assertThat(extractor.extract(headers).context())
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo("3000000000000000e457b5a2e4d86bd1"))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo("e457b5a2e4d86bd1"));
    headers.put(customTraceIdName, "3000000000000000");
    assertThat(extractor.extract(headers).context())
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo("3000000000000000"))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo("3000000000000000"));

    // Use the upper 64-bits (left 16 hex) as the environment ID
    //
    // This allows a lot of env numbers, and is easy to parse. This still gives 64-bit trace IDs
    headers.put(customTraceIdName, "0000000000000003e457b5a2e4d86bd1");
    assertThat(extractor.extract(headers).context())
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo("0000000000000003e457b5a2e4d86bd1"))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo("e457b5a2e4d86bd1"));
    headers.put(customTraceIdName, "3e457b5a2e4d86bd1");
    assertThat(extractor.extract(headers).context())
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo("0000000000000003e457b5a2e4d86bd1"))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo("e457b5a2e4d86bd1"));

    // Steal the upper nibble (first hex character) of the 64-bit trace ID as the environment ID
    //
    // This allows 15 env numbers, and is easy to parse. It allows 60-bits for the trace ID, which
    // is good enough for most sites.
    String customTraceIdString = "3457b5a2e4d86bd1";
    headers.put(customTraceIdName, customTraceIdString);
    TraceContext extractedContext = extractor.extract(headers).context();
    assertThat(extractedContext)
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo(customTraceIdString))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo(customTraceIdString))
      .satisfies(c -> assertThat((c.traceId() >>> 64 - 4L) & 0xf).isEqualTo(3));
  }

  @Test public void testB3SingleWins() {
    headers.put("b3", "1111111111111111-2222222222222222");
    headers.put(customTraceIdName, "1000000000000000e457b5a2e4d86bd1");
    assertThat(extractor.extract(headers).context())
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo("1111111111111111"))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo("2222222222222222"));
  }

  @Test public void testB3MultiWins() {
    headers.put("X-B3-TraceId", "1111111111111111");
    headers.put("X-B3-SpanId", "2222222222222222");
    headers.put(customTraceIdName, "1000000000000000e457b5a2e4d86bd1");
    assertThat(extractor.extract(headers).context())
      .satisfies(c -> assertThat(c.traceIdString()).isEqualTo("1111111111111111"))
      .satisfies(c -> assertThat(c.spanIdString()).isEqualTo("2222222222222222"));
  }
}
