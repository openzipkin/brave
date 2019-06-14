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
package brave.features.propagation;

import brave.Span;
import brave.Span.Kind;
import brave.Tracer;
import brave.Tracing;
import brave.features.propagation.SecondarySampling.Extra;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class SecondarySamplingTest {
  List<zipkin2.Span> zipkin = new ArrayList<>();
  List<MutableSpan> edge = new ArrayList<>(), links = new ArrayList<>(), triage =
    new ArrayList<>();
  FinishedSpanHandler triageHandler = new FinishedSpanHandler() {
    @Override public boolean handle(TraceContext context, MutableSpan span) {
      return triage.add(span);
    }
  };

  SecondarySampling secondarySampling = SecondarySampling.create()
    .putSystem("edge", new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        return edge.add(span);
      }
    })
    .putSystem("links", new FinishedSpanHandler() {
      @Override public boolean handle(TraceContext context, MutableSpan span) {
        return links.add(span);
      }
    });
  Tracing tracing = secondarySampling.build(Tracing.newBuilder().spanReporter(zipkin::add));

  TraceContext.Extractor<Map<String, String>> extractor = tracing.propagation().extractor(Map::get);
  TraceContext.Injector<Map<String, String>> injector = tracing.propagation().injector(Map::put);

  Map<String, String> map = new LinkedHashMap<>();

  @After public void close() {
    tracing.close();
  }

  /**
   * This shows when primary trace status is not sampled, we can send to handlers anyway.
   *
   * <p>At first, "triage" is not configured, so the tracer ignores it. Later, it is configured, so
   * starts receiving traces.
   */
  @Test public void integrationTest() {
    map.put("b3", "0");
    map.put("sampling", "edge:tps=1,ttl=3;links:sampled=1;triage:tps=5");

    Tracer tracer = tracing.tracer();
    Span span1 = tracer.nextSpan(extractor.extract(map)).name("span1").kind(Kind.SERVER).start();
    Span span2 = tracer.newChild(span1.context()).kind(Kind.CLIENT).name("span2").start();
    injector.inject(span2.context(), map);
    assertThat(map).containsEntry("sampling", "edge:ttl=3,sampled=1;links:sampled=1;triage:tps=5");

    // hop 1
    Span span3 = tracer.nextSpan(extractor.extract(map)).kind(Kind.SERVER).start();
    Span span4 = tracer.newChild(span3.context()).kind(Kind.CLIENT).name("span3").start();
    injector.inject(span4.context(), map);
    assertThat(map).containsEntry("sampling", "edge:sampled=1,ttl=2;links:sampled=1;triage:tps=5");

    // hop 2
    Span span5 = tracer.nextSpan(extractor.extract(map)).kind(Kind.SERVER).start();
    Span span6 = tracer.newChild(span5.context()).kind(Kind.CLIENT).name("span4").start();
    injector.inject(span6.context(), map);
    assertThat(map).containsEntry("sampling", "edge:sampled=1,ttl=1;links:sampled=1;triage:tps=5");

    // hop 3
    Span span7 = tracer.nextSpan(extractor.extract(map)).kind(Kind.SERVER).start();
    Span span8 = tracer.newChild(span7.context()).kind(Kind.CLIENT).name("span5").start();
    injector.inject(span8.context(), map);
    assertThat(map).containsEntry("sampling", "links:sampled=1;triage:tps=5");

    // dynamic configuration adds triage processing
    secondarySampling.putSystem("triage", triageHandler);

    // hop 4, triage is now sampled
    Span span9 = tracer.nextSpan(extractor.extract(map)).kind(Kind.SERVER).start();
    Span span10 = tracer.newChild(span9.context()).kind(Kind.CLIENT).name("span6").start();
    injector.inject(span10.context(), map);
    assertThat(map).containsEntry("sampling", "links:sampled=1;triage:sampled=1");

    asList(span1, span2, span3, span4, span5, span6, span7, span8, span9, span10)
      .forEach(Span::finish);

    assertThat(zipkin).isEmpty();
    assertThat(edge).filteredOn(s -> s.kind() == Kind.SERVER).hasSize(3);
    assertThat(links).filteredOn(s -> s.kind() == Kind.SERVER).hasSize(5);
    assertThat(triage).filteredOn(s -> s.kind() == Kind.SERVER).hasSize(1);
  }

  @Test public void extract_samplesLocalWhenConfigured() {
    map.put("b3", "0");
    map.put("sampling", "links:sampled=0;triage:tps=5");

    assertThat(extractor.extract(map).sampledLocal()).isFalse();

    map.put("b3", "0");
    map.put("sampling", "links:sampled=0;triage:sampled=1");

    assertThat(extractor.extract(map).sampledLocal()).isFalse();

    map.put("b3", "0");
    map.put("sampling", "links:sampled=1;triage:tps=5");

    assertThat(extractor.extract(map).sampledLocal()).isTrue();
  }

  /** This shows an example of dynamic configuration */
  @Test public void dynamicConfiguration() {
    // base case: links is configured, triage is not. triage is in the headers, though!
    map.put("b3", "0");
    map.put("sampling", "links:sampled=1;triage:tps=5");

    assertThat(extractor.extract(map).sampledLocal()).isTrue();

    // dynamic configuration removes link processing
    secondarySampling.removeSystem("links");
    assertThat(extractor.extract(map).sampledLocal()).isFalse();

    // dynamic configuration adds triage processing
    secondarySampling.putSystem("triage", triageHandler);
    assertThat(extractor.extract(map).sampledLocal()).isTrue();

    tracing.tracer().nextSpan(extractor.extract(map)).start().finish();
    assertThat(zipkin).isEmpty();
    assertThat(edge).isEmpty();
    assertThat(links).isEmpty(); // no longer configured
    assertThat(triage).hasSize(1); // now configured
  }

  @Test public void extract_convertsConfiguredTpsToDecision() {
    map.put("b3", "0");
    map.put("sampling", "edge:tps=1,ttl=3;links:sampled=0;triage:tps=5");

    TraceContextOrSamplingFlags extracted = extractor.extract(map);
    Extra extra = (Extra) extracted.extra().get(0);
    assertThat(extra.systems)
      .containsEntry("edge", twoEntryMap("sampled", "1", "ttl", "3"))
      .containsEntry("links", singletonMap("sampled", "0"))
      .containsEntry("triage", singletonMap("tps", "5")); // triage is not configured
  }

  @Test public void extract_decrementsTtlWhenConfigured() {
    map.put("b3", "0");
    map.put("sampling", "edge:sampled=1,ttl=3;links:sampled=0,ttl=1;triage:tps=5");

    TraceContextOrSamplingFlags extracted = extractor.extract(map);
    Extra extra = (Extra) extracted.extra().get(0);
    assertThat(extra.systems)
      .containsEntry("edge", twoEntryMap("sampled", "1", "ttl", "2"))
      .doesNotContainKey("links")
      .containsEntry("triage", singletonMap("tps", "5"));
  }

  @Test public void injectWritesAllSystems() {
    Extra extra = new Extra();
    extra.systems.put("edge", twoEntryMap("tps", "1", "ttl", "3"));
    extra.systems.put("links", singletonMap("sampled", "0"));
    extra.systems.put("triage", singletonMap("tps", "5"));

    injector.inject(TraceContext.newBuilder()
      .traceId(1L).spanId(2L)
      .sampled(false)
      .extra(singletonList(extra))
      .build(), map);

    assertThat(map)
      .containsEntry("sampling", "edge:tps=1,ttl=3;links:sampled=0;triage:tps=5");
  }

  static <K, V> Map<K, V> twoEntryMap(K key1, V value1, K key2, V value2) {
    Map<K, V> result = new LinkedHashMap<>();
    result.put(key1, value1);
    result.put(key2, value2);
    return result;
  }
}
