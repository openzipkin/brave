package brave.propagation;

import brave.Tracing;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static brave.propagation.Propagation.KeyFactory.STRING;
import static org.assertj.core.api.Assertions.assertThat;

public class ExtraFieldPropagationTest {
  String awsTraceId =
      "Root=1-67891233-abcdef012345678912345678;Parent=463ac35c9f6413ad;Sampled=1";
  String uuid = "f4308d05-2228-4468-80f6-92a8377ba193";
  Propagation.Factory factory = ExtraFieldPropagation.newFactory(
      B3Propagation.FACTORY, "x-vcap-request-id", "x-amzn-trace-id"
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

  @Test public void contextsAreIndependent() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(factory).build()) {

      TraceContext context1 = tracing.tracer().nextSpan().context();
      ExtraFieldPropagation.set(context1, "x-vcap-request-id", "foo");
      TraceContext context2 = tracing.tracer().newChild(context1).context();

      // same values when propagating down
      assertThat(ExtraFieldPropagation.get(context1, "x-vcap-request-id"))
          .isEqualTo(ExtraFieldPropagation.get(context2, "x-vcap-request-id"))
          .isEqualTo("foo");

      ExtraFieldPropagation.set(context1, "x-vcap-request-id", "bar");
      ExtraFieldPropagation.set(context2, "x-vcap-request-id", "baz");

      assertThat(ExtraFieldPropagation.get(context1, "x-vcap-request-id"))
          .isEqualTo("bar");
      assertThat(ExtraFieldPropagation.get(context2, "x-vcap-request-id"))
          .isEqualTo("baz");
    }
  }

  @Test public void contextIsntBrokenWithSmallChanges() {
    try (Tracing tracing = Tracing.newBuilder().propagationFactory(factory).build()) {

      TraceContext context1 = tracing.tracer().nextSpan().context();
      ExtraFieldPropagation.set(context1, "x-vcap-request-id", "foo");

      TraceContext context2 =
          tracing.tracer().toSpan(context1.toBuilder().sampled(false).build()).context();
      ExtraFieldPropagation.Extra extra1 = (ExtraFieldPropagation.Extra) context1.extra().get(0);
      ExtraFieldPropagation.Extra extra2 = (ExtraFieldPropagation.Extra) context2.extra().get(0);

      // we have the same span ID, so we should couple our extra fields
      assertThat(extra1).isSameAs(extra2);

      // we no longer have the same span ID, so we should decouple our extra fields
      TraceContext context3 =
          tracing.tracer().toSpan(context1.toBuilder().spanId(1L).build()).context();
      ExtraFieldPropagation.Extra extra3 = (ExtraFieldPropagation.Extra) context3.extra().get(0);

      // we have different instances of extra
      assertThat(extra1).isNotSameAs(extra3);

      // however, the values inside are the same until a write occurs
      assertThat(extra1.values).isSameAs(extra3.values);

      // inside the span, the same change is present, but the other span has the old values
      ExtraFieldPropagation.set(context1, "x-vcap-request-id", "1");
      assertThat(extra1.values).isSameAs(extra2.values);
      assertThat(extra1.values).isNotSameAs(extra3.values);
    }
  }

  @Test public void downcasesNames() {
    ExtraFieldPropagation.Factory factory =
        (ExtraFieldPropagation.Factory) ExtraFieldPropagation.newFactory(B3Propagation.FACTORY,
            "X-FOO");
    assertThat(factory.fieldNames)
        .containsExactly("x-foo");
  }

  @Test public void trimsNames() {
    ExtraFieldPropagation.Factory factory =
        (ExtraFieldPropagation.Factory) ExtraFieldPropagation.newFactory(B3Propagation.FACTORY,
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
    context = extractWithAmazonTraceId();

    assertThat(ExtraFieldPropagation.get(context, "x-amzn-trace-id"))
        .isEqualTo(awsTraceId);
  }

  @Test public void get_null_if_not_extraField() {
    assertThat(ExtraFieldPropagation.get(context, "x-amzn-trace-id"))
        .isNull();
  }

  @Test public void current_get() {
    context = extractWithAmazonTraceId();

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

  @Test public void set_ignoresUnconfiguredField() {
    ExtraFieldPropagation.set(context, "balloon-color", "red");

    assertThat(context.extra().get(0))
        .hasToString("ExtraFieldPropagation{}");
  }

  @Test public void get_ignoresUnconfiguredField() {
    assertThat(ExtraFieldPropagation.get("balloon-color"))
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

  @Test public void toString_extra() {
    String[] fieldNames = {"x-vcap-request-id"};
    ExtraFieldPropagation.Extra extra = new ExtraFieldPropagation.Extra(fieldNames);
    extra.set(0, uuid);

    assertThat(extra)
        .hasToString("ExtraFieldPropagation{x-vcap-request-id=" + uuid + "}");
  }

  @Test public void toString_two() {
    String[] fieldNames = {"x-amzn-trace-id", "x-vcap-request-id"};
    ExtraFieldPropagation.Extra extra = new ExtraFieldPropagation.Extra(fieldNames);
    extra.set(0, awsTraceId);
    extra.set(1, uuid);

    assertThat(extra).hasToString(
        "ExtraFieldPropagation{x-amzn-trace-id=" + awsTraceId + ", x-vcap-request-id=" + uuid + "}"
    );
  }

  @Test public void inject_extra() {
    ExtraFieldPropagation.Extra extra = ExtraFieldPropagation.findExtra(context.extra());
    extra.set(0, uuid);

    injector.inject(context, carrier);

    assertThat(carrier).containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void inject_two() {
    ExtraFieldPropagation.Extra extra = ExtraFieldPropagation.findExtra(context.extra());
    extra.set(0, uuid);
    extra.set(1, awsTraceId);

    injector.inject(context, carrier);

    assertThat(carrier)
        .containsEntry("x-amzn-trace-id", awsTraceId)
        .containsEntry("x-vcap-request-id", uuid);
  }

  @Test public void inject_prefixed() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
        .addField("x-vcap-request-id")
        .addPrefixedFields("baggage-", Arrays.asList("country-code"))
        .build();
    initialize();

    ExtraFieldPropagation.Extra extra = ExtraFieldPropagation.findExtra(context.extra());
    extra.set(0, uuid);
    extra.set(1, "FO");

    injector.inject(context, carrier);

    assertThat(carrier)
        .containsEntry("baggage-country-code", "FO")
        .containsEntry("x-vcap-request-id", uuid);
  }

  /** it is illegal to mix naming configuration in the same span */
  @Test(expected = IllegalStateException.class)
  public void mixed_names_unsupported() {
    Propagation.Factory otherFactory = ExtraFieldPropagation.newFactory(
        B3Propagation.FACTORY, "foo", "bar"
    );

    otherFactory.decorate(context);
  }

  @Test public void extract_extra() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);

    TraceContextOrSamplingFlags extracted = extractor.extract(carrier);
    assertThat(extracted.context().toBuilder().extra(Collections.emptyList()).build())
        .isEqualTo(context);
    assertThat(extracted.context().extra())
        .hasSize(1);

    ExtraFieldPropagation.Extra extra =
        (ExtraFieldPropagation.Extra) extracted.context().extra().get(0);
    assertThat(extra.values)
        .contains(awsTraceId);
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

    ExtraFieldPropagation.Extra extra =
        (ExtraFieldPropagation.Extra) extracted.context().extra().get(0);
    assertThat(extra.values)
        .containsExactly(uuid, awsTraceId);
  }

  @Test public void extract_prefixed() {
    factory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
        .addField("x-vcap-request-id")
        .addPrefixedFields("baggage-", Arrays.asList("country-code"))
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

    ExtraFieldPropagation.Extra extra =
        (ExtraFieldPropagation.Extra) extracted.context().extra().get(0);
    assertThat(extra.values)
        .containsExactly(uuid, "FO");
  }

  @Test public void getAll() {
    context = extractWithAmazonTraceId();

    assertThat(ExtraFieldPropagation.getAll(context))
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

  @Test public void getAll_empty_if_not_extraField() {
    assertThat(ExtraFieldPropagation.getAll(context))
        .isEmpty();
  }

  @Test public void current_getAll() {
    context = extractWithAmazonTraceId();

    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build();
         CurrentTraceContext.Scope scope = t.currentTraceContext().newScope(context)) {
      assertThat(ExtraFieldPropagation.getAll())
          .hasSize(1)
          .containsEntry("x-amzn-trace-id", awsTraceId);
    }
  }

  @Test public void current_getAll_empty_if_no_current_context() {
    try (Tracing t = Tracing.newBuilder().propagationFactory(factory).build()) {
      assertThat(ExtraFieldPropagation.getAll())
          .isEmpty();
    }
  }

  @Test public void current_getAll_empty_if_nothing_current() {
    assertThat(ExtraFieldPropagation.getAll())
        .isEmpty();
  }

  TraceContext extractWithAmazonTraceId() {
    injector.inject(context, carrier);
    carrier.put("x-amzn-trace-id", awsTraceId);
    return extractor.extract(carrier).context();
  }
}
