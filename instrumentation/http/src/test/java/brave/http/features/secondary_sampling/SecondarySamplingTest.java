/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.http.features.secondary_sampling;

import brave.http.features.secondary_sampling.SecondarySampling.Extra;
import brave.http.features.secondary_sampling.SecondarySamplingPolicy.Trigger;
import brave.propagation.B3SinglePropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.propagation.Propagation.KeyFactory.STRING;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is a <a href="https://github.com/openzipkin/openzipkin.github.io/wiki/secondary-sampling">Secondary
 * Sampling</a> proof of concept.
 */
public class SecondarySamplingTest {
  String serviceName = "auth", notServiceName = "gateway", notSpanId = "19f84f102048e047";
  SecondarySamplingPolicy policy = new SecondarySamplingPolicy();
  SecondarySampling secondarySampling =
    new SecondarySampling(serviceName, B3SinglePropagation.FACTORY, policy);

  Propagation<String> propagation = secondarySampling.create(STRING);

  Extractor<Map<String, String>> extractor = propagation.extractor(Map::get);
  Injector<Map<String, String>> injector = propagation.injector(Map::put);

  Map<String, String> headers = new LinkedHashMap<>();

  @Test public void extract_samplesLocalWhenConfigured() {
    // base case: links is configured, authcache is not. authcache is in the headers, though!
    policy.addTrigger("links", new Trigger());

    headers.put("b3", "0");
    headers.put("sampling", "authcache"); // sampling hint should not trigger

    assertThat(extractor.extract(headers).sampledLocal()).isFalse();

    headers.put("b3", "0");
    headers.put("sampling", "links,authcache;ttl=1"); // links should trigger

    assertThat(extractor.extract(headers).sampledLocal()).isTrue();
  }

  /** This shows that TTL is applied regardless of policy */
  @Test public void extract_ttlOverridesPolicy() {
    headers.put("b3", "0");
    headers.put("sampling", "links,authcache;ttl=1");

    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    Extra extra = (Extra) extracted.extra().get(0);
    assertThat(extra.sampledKeys)
      .containsExactly("authcache"); // not links because there's no trigger for that

    assertThat(extra.samplingKeyToParameters)
      .containsEntry("links", emptyMap())
      .containsEntry("authcache", emptyMap()); // no TTL left for the next  hop
  }

  /** This shows an example of dynamic configuration */
  @Test public void dynamicConfiguration() {
    // base case: links is configured, authcache is not. authcache is in the headers, though!
    policy.addTrigger("links", new Trigger());

    headers.put("b3", "0");
    headers.put("sampling", "links,authcache");

    assertThat(extractor.extract(headers).sampledLocal()).isTrue();

    // dynamic configuration removes link processing
    policy.removeTriggers("links");
    assertThat(extractor.extract(headers).sampledLocal()).isFalse();

    // dynamic configuration adds authcache processing
    policy.addTrigger("authcache", serviceName, new Trigger());
    assertThat(extractor.extract(headers).sampledLocal()).isTrue();
  }

  @Test public void extract_convertsConfiguredRpsToDecision() {
    policy.addTrigger("gatewayplay", notServiceName, new Trigger().rps(50));
    policy.addTrigger("links", new Trigger());
    policy.addTrigger("authcache", new Trigger().rps(100).ttl(1));

    headers.put("b3", "0");
    headers.put("sampling", "gatewayplay,links,authcache");

    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    Extra extra = (Extra) extracted.extra().get(0);
    assertThat(extra.sampledKeys)
      .containsExactly("links", "authcache"); // not gatewayplay as we aren't in that service

    assertThat(extra.samplingKeyToParameters)
      .containsEntry("gatewayplay", emptyMap())
      .containsEntry("links", emptyMap())
      // authcache triggers a ttl
      .containsEntry("authcache", singletonMap("ttl", "1"));
  }

  @Test public void extract_decrementsTtlEvenWhenNotConfigured() {
    headers.put("b3", "0");
    headers.put("sampling", "gatewayplay,authcache;ttl=2");

    TraceContextOrSamplingFlags extracted = extractor.extract(headers);
    Extra extra = (Extra) extracted.extra().get(0);

    assertThat(extra.sampledKeys)
      .containsExactly("authcache"); // not due to config, rather from TTL

    assertThat(extra.samplingKeyToParameters)
      .containsEntry("gatewayplay", emptyMap())
      .containsEntry("authcache", singletonMap("ttl", "1")); // one less TTL
  }

  @Test public void injectWritesNewLastParentWhenSampled() {
    Extra extra = new Extra();
    extra.samplingKeyToParameters.put("gatewayplay", singletonMap("spanId", notSpanId));
    extra.samplingKeyToParameters.put("links", emptyMap());
    extra.sampledKeys.add("links");
    extra.samplingKeyToParameters.put("authcache", twoEntryMap("ttl", "1", "spanId", notSpanId));

    TraceContext context = TraceContext.newBuilder()
      .traceId(1L).spanId(2L).sampled(false).extra(singletonList(extra)).build();
    injector.inject(context, headers);

    // doesn't interfere with keys not sampled.
    assertThat(headers).containsEntry("sampling",
      "gatewayplay;spanId=" + notSpanId + ","
        + "links;spanId=" + context.spanIdString() + ","
        + "authcache;ttl=1;spanId=" + notSpanId);
  }

  static <K, V> Map<K, V> twoEntryMap(K key1, V value1, K key2, V value2) {
    Map<K, V> result = new LinkedHashMap<>();
    result.put(key1, value1);
    result.put(key2, value2);
    return result;
  }
}
