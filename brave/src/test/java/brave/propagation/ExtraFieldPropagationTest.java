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
package brave.propagation;

import brave.Tracing;
import brave.internal.PropagationFields;
import brave.propagation.ExtraFieldPropagation.Extra;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Before;
import org.junit.Test;

import static brave.propagation.Propagation.KeyFactory.STRING;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class ExtraFieldPropagationTest {
  String awsTraceId =
    "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1";
  String uuid = "f4308d05-2228-4468-80f6-92a8377ba193";
  ExtraFieldPropagation.Factory factory = ExtraFieldPropagation.newFactory(
    B3SinglePropagation.FACTORY, "x-vcap-request-id", "x-amzn-trace-id"
  );

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
   * Ensure extra fields aren't leaked. This prevents tools from deleting entries when clearing a
   * trace.
   */
  @Test public void keysDontIncludeExtra() {
    assertThat(factory.create(Propagation.KeyFactory.STRING).keys())
      .isEqualTo(Propagation.B3_SINGLE_STRING.keys());
  }

  /**
   * Ensures OpenTracing 0.31 can read the extra keys, as its TextMap has no get by name function.
   */
  @Test public void extraKeysDontIncludeTraceContextKeys() {
    assertThat(factory.create(Propagation.KeyFactory.STRING).extraKeys())
      .containsExactly("x-vcap-request-id", "x-amzn-trace-id");
  }

  @Test public void downcasesNames() {
    ExtraFieldPropagation.Factory factory = ExtraFieldPropagation.newFactory(B3Propagation.FACTORY,
      "X-FOO");
    assertThat(factory.fieldNames)
      .containsExactly("x-foo");
  }

  @Test public void trimsNames() {
    ExtraFieldPropagation.Factory factory = ExtraFieldPropagation.newFactory(B3Propagation.FACTORY,
      " x-foo  ");
    assertThat(factory.fieldNames)
      .containsExactly("x-foo");
  }

  @Test(expected = NullPointerException.class) public void rejectsNull() {
    ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-me", null);
  }

  @Test(expected = IllegalArgumentException.class) public void rejectsEmpty() {
    ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-me", " ");
  }

  @Test public void get() {
    TraceContext context = extractWithAmazonTraceId();

    assertThat(ExtraFieldPropagation.get(context, "x-amzn-trace-id"))
      .isEqualTo(awsTraceId);
  }

  @Test public void get_null_if_not_extraField() {
    assertThat(ExtraFieldPropagation.get(context, "x-amzn-trace-id"))
      .isNull();
  }

  @Test public void current_get() {
    TraceContext context = extractWithAmazonTraceId();

    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         CurrentTraceContext.Scope scope = t.currentTraceContext().newScope(context)) {
      assertThat(ExtraFieldPropagation.get("x-amzn-trace-id"))
        .isEqualTo(awsTraceId);
    }
  }

  @Test public void current_get_null_if_no_current_context() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build()) {
      assertThat(ExtraFieldPropagation.get("x-amzn-trace-id"))
        .isNull();
    }
  }

  @Test public void current_get_null_if_nothing_current() {
    assertThat(ExtraFieldPropagation.get("x-amzn-trace-id"))
      .isNull();
  }

  @Test public void current_set() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         CurrentTraceContext.Scope scope = t.currentTraceContext().newScope(context)) {
      ExtraFieldPropagation.set("x-amzn-trace-id", awsTraceId);

      assertThat(ExtraFieldPropagation.get("x-amzn-trace-id"))
        .isEqualTo(awsTraceId);
    }
  }

  @Test public void current_set_noop_if_no_current_context() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build()) {
      ExtraFieldPropagation.set("x-amzn-trace-id", awsTraceId); // doesn't throw
    }
  }

  @Test public void current_set_noop_if_nothing_current() {
    ExtraFieldPropagation.set("x-amzn-trace-id", awsTraceId); // doesn't throw
  }

  @Test public void inject_extra() {
    PropagationFields fields = context.findExtra(Extra.class);
    fields.put("x-vcap-request-id", uuid);

    injector.inject(context, carrier);

    assertThat(carrier).containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void inject_two() {
    PropagationFields fields = context.findExtra(Extra.class);
    fields.put("x-vcap-request-id", uuid);
    fields.put("x-amzn-trace-id", awsTraceId);

    injector.inject(context, carrier);

    assertThat(carrier)
      .containsEntry("x-amzn-trace-id", awsTraceId)
      .containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void inject_prefixed() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .addField("x-vcap-request-id")
      .addPrefixedFields("baggage-", asList("country-code"))
      .build();
    initialize();

    PropagationFields fields = context.findExtra(Extra.class);
    fields.put("x-vcap-request-id", uuid);
    fields.put("country-code", "FO");

    injector.inject(context, carrier);

    assertThat(carrier)
      .containsEntry("baggage-country-code", "FO")
      .containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void extract_extra() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(1);

    PropagationFields fields = (PropagationFields) extracted.context().extra().get(0);
    assertThat(fields.toMap())
      .containsEntry("x-amzn-trace-id", awsTraceId);
  }

  @Test public void extract_two() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);
    carrier.put("x-vcap-request-id", uuid);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(1);

    PropagationFields fields = (PropagationFields) extracted.context().extra().get(0);
    assertThat(fields.toMap())
      .containsEntry("x-amzn-trace-id", awsTraceId)
      .containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void extract_prefixed() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .addField("x-vcap-request-id")
      .addPrefixedFields("baggage-", asList("country-code"))
      .build();
    initialize();

    injector.inject(context, carrier);
    carrier.put("baggage-country-code", "FO");
    carrier.put("x-vcap-request-id", uuid);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
      .isEqualTo(context);
    assertThat(extracted.context().extra())
      .hasSize(1);

    PropagationFields fields = (PropagationFields) extracted.context().extra().get(0);
    assertThat(fields.toMap())
      .containsEntry("country-code", "FO")
      .containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void getAll() {
    TraceContext context = extractWithAmazonTraceId();

    assertThat(ExtraFieldPropagation.getAll(context))
      .hasSize(1)
      .containsEntry("x-amzn-trace-id", awsTraceId);
  }

  @Test public void getAll_extracted() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);

    assertThat(ExtraFieldPropagation.getAll(extracted))
      .hasSize(1)
      .containsEntry("x-amzn-trace-id", awsTraceId);
  }

  @Test public void getAll_extractedWithContext() {
    carrier.put("x-amzn-trace-id", awsTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);

    assertThat(ExtraFieldPropagation.getAll(extracted))
      .hasSize(1)
      .containsEntry("x-amzn-trace-id", awsTraceId);
  }

  @Test public void getAll_two() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);
    carrier.put("x-vcap-request-id", uuid);

    context = extractor.extract(carrier).context();

    assertThat(ExtraFieldPropagation.getAll(context))
      .hasSize(2)
      .containsEntry("x-amzn-trace-id", awsTraceId)
      .containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void getAll_empty_if_no_extraField() {
    assertThat(ExtraFieldPropagation.getAll(context))
      .isEmpty();
  }

  @Test public void extract_field_multiple_prefixes() {
    // switch to case insensitive as this example is about http :P
    carrier = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .addField("userId")
      .addField("sessionId")
      .addPrefixedFields("baggage-", asList("userId", "sessionId"))
      .addPrefixedFields("baggage_", asList("userId", "sessionId"))
      .build();
    initialize();

    injector.inject(context, carrier);
    carrier.put("baggage-userId", "bob");
    carrier.put("baggage-sessionId", "12345");

    context = extractor.extract(carrier).context();

    assertThat(ExtraFieldPropagation.get(context, "userId"))
      .isEqualTo("bob");
    assertThat(ExtraFieldPropagation.get(context, "sessionId"))
      .isEqualTo("12345");
  }

  /** Redaction only applies outbound. Inbound parsing should be unaffected */
  @Test public void extract_redactedField() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .addRedactedField("userid")
      .addField("sessionid")
      .build();
    initialize();

    injector.inject(context, carrier);
    carrier.put("userid", "bob");
    carrier.put("sessionid", "12345");

    context = extractor.extract(carrier).context();

    assertThat(ExtraFieldPropagation.get(context, "userid"))
      .isEqualTo("bob");
    assertThat(ExtraFieldPropagation.get(context, "sessionid"))
      .isEqualTo("12345");
  }

  /** Redaction prevents named fields from being written downstream. */
  @Test public void inject_redactedField() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .addRedactedField("userid")
      .addField("sessionid")
      .build();
    initialize();

    ExtraFieldPropagation.set(context, "userid", "bob");
    ExtraFieldPropagation.set(context, "sessionid", "12345");

    injector.inject(context, carrier);

    assertThat(carrier)
      .doesNotContainKey("userid")
      .containsEntry("sessionid", "12345");
  }

  @Test public void inject_field_multiple_prefixes() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3SinglePropagation.FACTORY)
      .addField("userId")
      .addField("sessionId")
      .addPrefixedFields("baggage-", asList("userId", "sessionId"))
      .addPrefixedFields("baggage_", asList("userId", "sessionId"))
      .build();
    initialize();

    ExtraFieldPropagation.set(context, "userId", "bob");
    ExtraFieldPropagation.set(context, "sessionId", "12345");

    injector.inject(context, carrier);

    // NOTE: the labels are downcased
    assertThat(carrier).containsExactly(
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
    assertThat(ExtraFieldPropagation.newFactoryBuilder(B3SinglePropagation.FACTORY)
      .addField("country-code")
      .addPrefixedFields("baggage-", asList("country-code"))
      .addPrefixedFields("baggage_", asList("country-code"))
      .build())
      .usingRecursiveComparison().isEqualTo(
      ExtraFieldPropagation.newFactoryBuilder(B3SinglePropagation.FACTORY)
        .addField("country-code").addField("country-code")
        .addPrefixedFields("baggage-", asList("country-code", "country-code"))
        .addPrefixedFields("baggage_", asList("country-code", "country-code"))
        .build()
    );
  }

  TraceContext extractWithAmazonTraceId() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);
    return extractor.extract(carrier).context();
  }
}
