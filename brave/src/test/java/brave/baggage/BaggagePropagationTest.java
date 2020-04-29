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
package brave.baggage;

import brave.baggage.BaggagePropagation.AllKeyNames;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.internal.baggage.ExtraBaggageFields;
import brave.propagation.B3Propagation;
import brave.propagation.B3SingleFormat;
import brave.propagation.B3SinglePropagation;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Before;
import org.junit.Test;

import static brave.baggage.BaggagePropagation.newFactoryBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class BaggagePropagationTest {
  BaggageField vcapRequestId = BaggageField.create("x-vcap-request-id");
  BaggageField amznTraceId = BaggageField.create("x-amzn-trace-id");
  String awsTraceId =
    "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1";
  String uuid = "f4308d05-2228-4468-80f6-92a8377ba193";
  Propagation.Factory factory = newFactoryBuilder(B3Propagation.FACTORY)
    .add(SingleBaggageField.remote(vcapRequestId))
    .add(SingleBaggageField.remote(amznTraceId)).build();

  Map<String, String> request = new LinkedHashMap<>();
  Injector<Map<String, String>> injector;
  Extractor<Map<String, String>> extractor;
  TraceContext context;

  @Before public void initialize() {
    injector = factory.get().injector(Map::put);
    extractor = factory.get().extractor(Map::get);
    context = factory.decorate(TraceContext.newBuilder()
      .traceId(1L)
      .spanId(2L)
      .sampled(true)
      .build());
  }

  /**
   * Ensure baggage isn't leaked. This prevents tools from deleting entries when clearing a trace.
   */
  @Test public void keysDontIncludeBaggage() {
    assertThat(factory.get().keys())
      .isEqualTo(B3Propagation.B3_STRING.keys());
  }

  @Test public void newFactory_noFields() {
    assertThat(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY).build())
      .isSameAs(B3Propagation.FACTORY);
  }

  @Test public void newFactory_sharingRemoteName() {
    BaggagePropagation.FactoryBuilder builder = newFactoryBuilder(B3Propagation.FACTORY);
    SingleBaggageField userName =
      SingleBaggageField.newBuilder(BaggageField.create("userName")).addKeyName("baggage").build();
    SingleBaggageField userId =
      SingleBaggageField.newBuilder(BaggageField.create("userId")).addKeyName("baggage").build();
    builder.add(userName);
    assertThatThrownBy(() -> builder.add(userId))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Propagation key already in use: baggage");
  }

  @Test public void clear_and_add() {
    SingleBaggageField requestIdConfig = SingleBaggageField.newBuilder(vcapRequestId)
      .addKeyName("request-id")
      .addKeyName("request_id")
      .build();

    SingleBaggageField traceIdConfig = SingleBaggageField.remote(amznTraceId);
    BaggagePropagation.FactoryBuilder builder = newFactoryBuilder(B3Propagation.FACTORY)
      .add(requestIdConfig)
      .add(traceIdConfig);

    Set<BaggagePropagationConfig> configs = builder.configs();

    builder.clear();

    configs.forEach(builder::add);

    assertThat(builder)
      .usingRecursiveComparison()
      .isEqualTo(newFactoryBuilder(B3Propagation.FACTORY)
        .add(requestIdConfig)
        .add(traceIdConfig));
  }

  @Test public void inject_baggage() {
    ExtraBaggageFields baggage = context.findExtra(ExtraBaggageFields.class);
    baggage.updateValue(vcapRequestId, uuid);

    injector.inject(context, request);

    assertThat(request).containsEntry(vcapRequestId.name(), uuid);
  }

  @Test public void inject_two() {
    ExtraBaggageFields baggage = context.findExtra(ExtraBaggageFields.class);
    baggage.updateValue(vcapRequestId, uuid);
    baggage.updateValue(amznTraceId, awsTraceId);

    injector.inject(context, request);

    assertThat(request)
      .containsEntry(amznTraceId.name(), awsTraceId)
      .containsEntry(vcapRequestId.name(), uuid);
  }

  /** Less overhead and distraction for the edge case of correlation-only. */
  @Test public void extract_baggage_onlyOneExtraWhenNothingRemote() {
    Propagation.Factory factory = newFactoryBuilder(B3Propagation.FACTORY)
        .add(SingleBaggageField.local(vcapRequestId))
        .add(SingleBaggageField.local(amznTraceId)).build();
    extractor = factory.get().extractor(Map::get);

    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    assertThat(extracted.extra())
        .hasSize(1)
        .noneMatch(AllKeyNames.class::isInstance);
  }

  @Test public void extract_baggage_addsAllKeyNames_evenWhenEmpty() {
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    assertThat(extracted.extra()).hasSize(2);
    assertThat(extracted.extra().get(1))
        .asInstanceOf(InstanceOfAssertFactories.type(AllKeyNames.class))
        .extracting(a -> a.list)
        .asInstanceOf(InstanceOfAssertFactories.list(String.class))
        .containsExactly("x-vcap-request-id", "x-amzn-trace-id");
  }

  @Test public void extract_baggage() {
    injector.inject(context, request);
    request.put(amznTraceId.name(), awsTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(2);

    assertThat(amznTraceId.getValue(extracted))
      .isEqualTo(awsTraceId);
  }

  @Test public void extract_two() {
    injector.inject(context, request);
    request.put(amznTraceId.name(), awsTraceId);
    request.put(vcapRequestId.name(), uuid);

    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(2);

    assertThat(amznTraceId.getValue(extracted))
      .isEqualTo(awsTraceId);
    assertThat(vcapRequestId.getValue(extracted))
      .isEqualTo(uuid);
  }

  @Test public void extract_field_multiple_key_names() {
    // switch to case insensitive as this example is about http :P
    request = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    BaggageField userId = BaggageField.create("userId");
    BaggageField sessionId = BaggageField.create("sessionId");

    SingleBaggageField userIdConfig = SingleBaggageField.newBuilder(userId)
      .addKeyName("baggage-userId")
      .addKeyName("baggage_userId")
      .build();

    SingleBaggageField sessionIdConfig = SingleBaggageField.newBuilder(sessionId)
      .addKeyName("baggage-sessionId")
      .addKeyName("baggage_sessionId")
      .build();

    factory = newFactoryBuilder(B3Propagation.FACTORY)
      .add(userIdConfig)
      .add(sessionIdConfig)
      .build();
    initialize();

    injector.inject(context, request);
    request.put("baggage-userId", "bob");
    request.put("baggage-sessionId", "12345");

    context = extractor.extract(request).context();

    assertThat(userId.getValue(context)).isEqualTo("bob");
    assertThat(sessionId.getValue(context)).isEqualTo("12345");
  }

  @Test public void extract_no_overridden_key_names() {
    BaggageField userId = BaggageField.create("userId");
    BaggageField sessionId = BaggageField.create("sessionId");

    factory = newFactoryBuilder(B3Propagation.FACTORY)
      .add(SingleBaggageField.local(userId))
      .add(SingleBaggageField.remote(sessionId))
      .build();
    initialize();

    injector.inject(context, request);
    request.put("userid", "bob");
    request.put("sessionid", "12345");

    context = extractor.extract(request).context();

    assertThat(userId.getValue(context)).isNull();
    assertThat(sessionId.getValue(context)).isEqualTo("12345");
  }

  /** Redaction prevents named fields from being written downstream. */
  @Test public void inject_no_key_names() {
    BaggageField userId = BaggageField.create("userId");
    BaggageField sessionId = BaggageField.create("sessionId");

    factory = newFactoryBuilder(B3SinglePropagation.FACTORY)
      .add(SingleBaggageField.local(userId))
      .add(SingleBaggageField.remote(sessionId))
      .build();
    initialize();

    userId.updateValue(context, "bob");
    sessionId.updateValue(context, "12345");

    injector.inject(context, request);

    assertThat(request)
      .doesNotContainKey("userid")
      .containsEntry("sessionid", "12345");
  }

  @Test public void inject_field_multiple_key_names() {
    BaggageField userId = BaggageField.create("userId");
    BaggageField sessionId = BaggageField.create("sessionId");

    SingleBaggageField userIdConfig = SingleBaggageField.newBuilder(userId)
      .addKeyName("userId")
      .addKeyName("baggage-userId")
      .addKeyName("baggage_userId")
      .build();

    SingleBaggageField sessionIdConfig = SingleBaggageField.newBuilder(sessionId)
      .addKeyName("sessionId")
      .addKeyName("baggage-sessionId")
      .addKeyName("baggage_sessionId")
      .build();

    factory = newFactoryBuilder(B3SinglePropagation.FACTORY)
      .add(userIdConfig)
      .add(sessionIdConfig)
      .build();
    initialize();

    userId.updateValue(context, "bob");
    sessionId.updateValue(context, "12345");

    injector.inject(context, request);

    // NOTE: the labels are downcased
    assertThat(request).containsOnly(
      entry("b3", B3SingleFormat.writeB3SingleFormat(context)),
      entry("userid", "bob"),
      entry("sessionid", "12345"),
      entry("baggage-userid", "bob"),
      entry("baggage-sessionid", "12345"),
      entry("baggage_userid", "bob"),
      entry("baggage_sessionid", "12345")
    );
  }

  @Test public void dupesNotOk() {
    SingleBaggageField userIdConfig = SingleBaggageField.local(BaggageField.create("userId"));
    BaggagePropagation.FactoryBuilder builder = newFactoryBuilder(B3Propagation.FACTORY)
      .add(userIdConfig);
    assertThatThrownBy(() -> builder.add(userIdConfig))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test public void allKeyNames_baggagePropagation() {
    Propagation.Factory factory = BaggagePropagation.newFactoryBuilder(B3SinglePropagation.FACTORY)
      .add(SingleBaggageField.local(BaggageField.create("redacted"))) // local shouldn't return
      .add(SingleBaggageField.remote(BaggageField.create("user-id")))
      .add(SingleBaggageField.remote(BaggageField.create("session-id"))).build();

    assertThat(BaggagePropagation.allKeyNames(factory.get()))
      .containsExactly("b3", "user-id", "session-id");
  }

  @Test public void allKeyNames_baggagePropagation_noRemote() {
    Propagation.Factory factory = BaggagePropagation.newFactoryBuilder(B3SinglePropagation.FACTORY)
        .add(SingleBaggageField.local(BaggageField.create("redacted"))) // local shouldn't return
        .add(SingleBaggageField.local(BaggageField.create("user-id")))
        .add(SingleBaggageField.local(BaggageField.create("session-id"))).build();

    assertThat(BaggagePropagation.allKeyNames(factory.get()))
        .containsExactly("b3");
  }

  @Test public void allKeyNames_extraFieldPropagation() {
    ExtraFieldPropagation.Factory factory =
      ExtraFieldPropagation.newFactory(B3SinglePropagation.FACTORY, "user-id", "session-id");

    assertThat(BaggagePropagation.allKeyNames(factory.get()))
      .containsExactly("b3", "user-id", "session-id");
  }
}
