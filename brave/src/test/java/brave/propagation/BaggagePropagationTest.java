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
package brave.propagation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;

import static brave.propagation.BaggagePropagation.newFactoryBuilder;
import static brave.propagation.Propagation.KeyFactory.STRING;
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
    .addField(vcapRequestId)
    .addField(amznTraceId).build();

  Map<String, String> carrier = new LinkedHashMap<>();
  TraceContext.Injector<Map<String, String>> injector;
  TraceContext.Extractor<Map<String, String>> extractor;
  TraceContext context;

  @Before public void initialize() {
    injector = factory.create(STRING).injector(Map::put);
    extractor = factory.create(STRING).extractor(Map::get);
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
    assertThat(factory.create(Propagation.KeyFactory.STRING).keys())
      .isEqualTo(B3Propagation.B3_STRING.keys());
  }

  @Test public void newFactory_noFields() {
    assertThat(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY).build())
      .isSameAs(B3Propagation.FACTORY);
  }

  @Test public void newFactory_sharingRemoteName() {
    BaggagePropagation.FactoryBuilder builder = newFactoryBuilder(B3Propagation.FACTORY);
    builder.addField(BaggageField.newBuilder("userName").addRemoteName("baggage").build());
    builder.addField(BaggageField.newBuilder("userId").addRemoteName("baggage").build());

    assertThatThrownBy(builder::build)
      .isInstanceOf(UnsupportedOperationException.class)
      .hasMessage("[userName, userId] have the same remote name: baggage");
  }

  @Test public void inject_baggage() {
    PredefinedBaggageFields baggage = context.findExtra(PredefinedBaggageFields.class);
    baggage.put(vcapRequestId, uuid);

    injector.inject(context, carrier);

    assertThat(carrier).containsEntry(vcapRequestId.name(), uuid);
  }

  @Test public void inject_two() {
    PredefinedBaggageFields baggage = context.findExtra(PredefinedBaggageFields.class);
    baggage.put(vcapRequestId, uuid);
    baggage.put(amznTraceId, awsTraceId);

    injector.inject(context, carrier);

    assertThat(carrier)
      .containsEntry(amznTraceId.name(), awsTraceId)
      .containsEntry(vcapRequestId.name(), uuid);
  }

  @Test public void extract_baggage() {
    injector.inject(context, carrier);
    carrier.put(amznTraceId.name(), awsTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(1);

    PredefinedBaggageFields baggage =
      (PredefinedBaggageFields) extracted.context().extra().get(0);
    assertThat(baggage.toMap())
      .containsEntry(amznTraceId.name(), awsTraceId);
  }

  @Test public void extract_two() {
    injector.inject(context, carrier);
    carrier.put(amznTraceId.name(), awsTraceId);
    carrier.put(vcapRequestId.name(), uuid);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(1);

    PredefinedBaggageFields baggage = (PredefinedBaggageFields) extracted.context().extra().get(0);
    assertThat(baggage.toMap())
      .containsEntry(amznTraceId.name(), awsTraceId)
      .containsEntry(vcapRequestId.name(), uuid);
  }

  @Test public void extract_field_multiple_remote_names() {
    // switch to case insensitive as this example is about http :P
    carrier = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    BaggageField userId = BaggageField.newBuilder("userId")
      .addRemoteName("baggage-userId")
      .addRemoteName("baggage_userId").build();
    BaggageField sessionId = BaggageField.newBuilder("sessionId")
      .addRemoteName("baggage-sessionId")
      .addRemoteName("baggage_sessionId").build();

    factory = newFactoryBuilder(B3Propagation.FACTORY)
      .addField(userId)
      .addField(sessionId)
      .build();
    initialize();

    injector.inject(context, carrier);
    carrier.put("baggage-userId", "bob");
    carrier.put("baggage-sessionId", "12345");

    context = extractor.extract(carrier).context();

    assertThat(userId.getValue(context)).isEqualTo("bob");
    assertThat(sessionId.getValue(context)).isEqualTo("12345");
  }

  @Test public void extract_no_remote_names() {
    BaggageField userId = BaggageField.newBuilder("userId")
      .clearRemoteNames().build();
    BaggageField sessionId = BaggageField.create("sessionId");

    factory = newFactoryBuilder(B3Propagation.FACTORY)
      .addField(userId)
      .addField(sessionId)
      .build();
    initialize();

    injector.inject(context, carrier);
    carrier.put("userid", "bob");
    carrier.put("sessionid", "12345");

    context = extractor.extract(carrier).context();

    assertThat(userId.getValue(context)).isNull();
    assertThat(sessionId.getValue(context)).isEqualTo("12345");
  }

  /** Redaction prevents named fields from being written downstream. */
  @Test public void inject_redactedField() {
    BaggageField userId = BaggageField.newBuilder("userId")
      .clearRemoteNames().build();
    BaggageField sessionId = BaggageField.create("sessionId");

    factory = newFactoryBuilder(B3SinglePropagation.FACTORY)
      .addField(userId)
      .addField(sessionId)
      .build();
    initialize();

    userId.updateValue(context, "bob");
    sessionId.updateValue(context, "12345");

    injector.inject(context, carrier);

    assertThat(carrier)
      .doesNotContainKey("userid")
      .containsEntry("sessionid", "12345");
  }

  @Test public void inject_field_multiple_prefixes() {
    BaggageField userId = BaggageField.newBuilder("userId")
      .addRemoteName("baggage-userId")
      .addRemoteName("baggage_userId").build();
    BaggageField sessionId = BaggageField.newBuilder("sessionId")
      .addRemoteName("baggage-sessionId")
      .addRemoteName("baggage_sessionId").build();

    factory = newFactoryBuilder(B3SinglePropagation.FACTORY)
      .addField(userId)
      .addField(sessionId)
      .build();
    initialize();

    userId.updateValue(context, "bob");
    sessionId.updateValue(context, "12345");

    injector.inject(context, carrier);

    // NOTE: the labels are downcased
    assertThat(carrier).containsOnly(
      entry("b3", B3SingleFormat.writeB3SingleFormat(context)),
      entry("userid", "bob"),
      entry("sessionid", "12345"),
      entry("baggage-userid", "bob"),
      entry("baggage-sessionid", "12345"),
      entry("baggage_userid", "bob"),
      entry("baggage_sessionid", "12345")
    );
  }

  @Test public void deduplicates() {
    assertThat(newFactoryBuilder(B3Propagation.FACTORY)
      .addField(BaggageField.create("userId"))
      .addField(BaggageField.create("userId"))
      .build())
      .usingRecursiveComparison().isEqualTo(
      newFactoryBuilder(B3Propagation.FACTORY)
        .addField(BaggageField.create("userId"))
        .build()
    );
  }
}
