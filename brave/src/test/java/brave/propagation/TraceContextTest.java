/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import brave.internal.codec.HexCodec;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.internal.InternalPropagation.FLAG_SHARED;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceContextTest {
  TraceContext base = TraceContext.newBuilder().traceId(1L).spanId(1L).build();

  @Test void traceIdRequiredAndNonZero() {
    assertThatThrownBy(() -> TraceContext.newBuilder().spanId(2L).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Missing: traceId");
  }

  @Test void spanIdRequiredAndNonZero() {
    assertThatThrownBy(() -> TraceContext.newBuilder().build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Missing: traceId spanId");

    assertThatThrownBy(() -> TraceContext.newBuilder().traceIdHigh(1L).build())
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Missing: spanId");
  }

  @Test void compareUnequalIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3L).build();

    assertThat(context)
      .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build());
    assertThat(context.hashCode())
      .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build().hashCode());
  }

  @Test void contextWithShared_true() {
    assertThat(base.toBuilder().sampled(false).shared(true).build().flags)
      .isEqualTo(FLAG_SAMPLED_SET | FLAG_SHARED);
  }

  @Test void contextWithShared_false() {
    assertThat(base.toBuilder().sampled(false).shared(false).build().flags)
      .isEqualTo(FLAG_SAMPLED_SET);
  }

  /**
   * Shared context is different than an unshared one, notably this keeps client/server loopback
   * separate.
   */
  @Test void compareUnequalIds_onShared() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3L).build();

    assertThat(context)
      .isNotEqualTo(context.toBuilder().shared(true).build());
    assertThat(context.hashCode())
      .isNotEqualTo(context.toBuilder().shared(true).build().hashCode());
  }

  @Test void compareEqualIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
      .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build());
    assertThat(context.hashCode())
      .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build().hashCode());
  }

  @Test void equalOnSameTraceIdSpanId() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
      .isEqualTo(context.toBuilder().parentId(1L).build());
    assertThat(context.hashCode())
      .isEqualTo(context.toBuilder().parentId(1L).build().hashCode());
  }

  @Test void testToString_lo() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3).parentId(2L).build();

    assertThat(context.toString())
      .isEqualTo("000000000000014d/0000000000000003");
  }

  @Test void testToString() {
    TraceContext context =
      TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).parentId(2L).build();

    assertThat(context.toString())
      .isEqualTo("000000000000014d00000000000001bc/0000000000000003");
  }

  @Test void canUsePrimitiveOverloads_true() {
    TraceContext primitives = base.toBuilder()
      .parentId(1L)
      .sampled(true)
      .debug(true)
      .build();

    TraceContext objects = base.toBuilder()
      .parentId(Long.valueOf(1L))
      .sampled(Boolean.TRUE)
      .debug(Boolean.TRUE)
      .build();

    assertThat(primitives)
      .isEqualToComparingFieldByField(objects);
    assertThat(primitives.debug())
      .isTrue();
    assertThat(primitives.sampled())
      .isTrue();
  }

  @Test void canUsePrimitiveOverloads_false() {
    base = base.toBuilder().debug(true).build();

    TraceContext primitives = base.toBuilder()
      .parentId(1L)
      .sampled(false)
      .debug(false)
      .build();

    TraceContext objects = base.toBuilder()
      .parentId(Long.valueOf(1L))
      .sampled(Boolean.FALSE)
      .debug(Boolean.FALSE)
      .build();

    assertThat(primitives)
      .isEqualToComparingFieldByField(objects);
    assertThat(primitives.debug())
      .isFalse();
    assertThat(primitives.sampled())
      .isFalse();
  }

  @Test void canSetSampledNull() {
    base = base.toBuilder().sampled(true).build();

    TraceContext objects = base.toBuilder().sampled(null).build();

    assertThat(objects.debug())
      .isFalse();
    assertThat(objects.sampled())
      .isNull();
  }

  @Test void nullToZero() {
    TraceContext nulls = base.toBuilder()
      .parentId(null)
      .build();

    TraceContext zeros = base.toBuilder()
      .parentId(0L)
      .build();

    assertThat(nulls)
      .isEqualToComparingFieldByField(zeros);
  }

  @Test void parseTraceId_128bit() {
    String traceIdString = "463ac35c9f6413ad48485a3953bb6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
      .isEqualTo("463ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
      .isEqualTo("48485a3953bb6124");
  }

  @Test void parseTraceId_64bit() {
    String traceIdString = "48485a3953bb6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
      .isEqualTo(traceIdString);
  }

  @Test void parseTraceId_short128bit() {
    String traceIdString = "3ac35c9f6413ad48485a3953bb6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
      .isEqualTo("003ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
      .isEqualTo("48485a3953bb6124");
  }

  @Test void parseTraceId_short64bit() {
    String traceIdString = "6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
      .isEqualTo("000000000000" + traceIdString);
  }

  @Test void parseTraceId_padded() {
    TraceContext context = parseGoodTraceID("00000000000000000000000000000001")
      .spanId(2L).build();

    assertThat(context.traceIdHigh()).isZero();
    assertThat(context.traceId()).isOne();
  }

  /** Not a good idea to pad backwards, but it is a valid value */
  @Test void parseTraceId_paddedRight() {
    TraceContext context = parseGoodTraceID("10000000000000000000000000000000")
      .spanId(2L).build();

    assertThat(context.traceIdHigh())
      .isEqualTo(Long.parseUnsignedLong("1000000000000000", 16));
    assertThat(context.traceId()).isZero();
  }

  /**
   * Trace ID is a required parameter, so it cannot be null empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test void parseTraceId_malformedReturnsFalse() {
    parseBadTraceId("00000000000000000000000000000000");
    parseBadTraceId("463acL$c9f6413ad48485a3953bb6124");
    parseBadTraceId("holy 💩");
    parseBadTraceId("-");
    parseBadTraceId("");
    parseBadTraceId(null);
  }

  @Test void parseSpanId() {
    String spanIdString = "48485a3953bb6124";

    TraceContext.Builder builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
      .isEqualTo(spanIdString);
  }

  @Test void parseSpanId_short64bit() {
    String spanIdString = "6124";

    TraceContext.Builder builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
      .isEqualTo("000000000000" + spanIdString);
  }

  /**
   * Span ID is a required parameter, so it cannot be null empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test void parseSpanId_malformedReturnsFalse() {
    parseBadSpanId("463acL$c9f6413ad");
    parseBadSpanId("holy 💩");
    parseBadSpanId("-");
    parseBadSpanId("");
    parseBadSpanId(null);
  }

  TraceContext.Builder parseGoodSpanId(String spanIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
      .isTrue();
    return builder;
  }

  void parseBadSpanId(String spanIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
      .isFalse();
    assertThat(builder.spanId).isZero();
  }

  @Test void parseParentId() {
    String parentIdString = "48485a3953bb6124";

    TraceContext.Builder builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
      .isEqualTo(parentIdString);
  }

  @Test void parseParentId_null_is_ok() {
    TraceContext.Builder builder = parseGoodParentId(null);

    assertThat(builder.parentId).isZero();
  }

  @Test void parseParentId_short64bit() {
    String parentIdString = "6124";

    TraceContext.Builder builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
      .isEqualTo("000000000000" + parentIdString);
  }

  /**
   * Parent Span ID is an optional parameter, but it cannot be empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test void parseParentId_malformedReturnsFalse() {
    parseBadParentId("463acL$c9f6413ad");
    parseBadParentId("holy 💩");
    parseBadParentId("-");
    parseBadParentId("");
  }

  TraceContext.Builder parseGoodParentId(String parentIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
      .isTrue();
    return builder;
  }

  void parseBadParentId(String parentIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
      .isFalse();
    assertThat(builder.parentId).isZero();
  }

  TraceContext.Builder parseGoodTraceID(String traceIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
      .isTrue();
    return builder;
  }

  void parseBadTraceId(String traceIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
      .isFalse();
    assertThat(builder.traceIdHigh).isZero();
    assertThat(builder.traceId).isZero();
  }

  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();

  @Test void withExtra_notEmpty() {
    assertThat(context.withExtra(Arrays.asList(1L)))
      .extracting("extraList")
      .isEqualTo(Arrays.asList(1L));
  }

  @Test void withExtra_empty() {
    assertThat(context.toBuilder().addExtra(1L).build().withExtra(emptyList()))
      .extracting("extraList")
      .isEqualTo(emptyList());
  }

  @Test void caches_hashCode() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3L).build();

    assertThat(context.hashCode).isZero();
    assertThat(context.hashCode())
      .isNotZero()
      .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(3L).build().hashCode());
  }

  @Test void traceIdString_caches() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();

    assertThat(context.traceIdString).isNull();
    assertThat(context.traceIdString())
      .isNotNull()
      .isEqualTo("0000000000000001");
    assertThat(context.traceIdString)
      .isEqualTo("0000000000000001");
  }

  @Test void parentIdString_caches() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).parentId(2L).spanId(3L).build();

    assertThat(context.parentIdString).isNull();
    assertThat(context.parentIdString())
      .isNotNull()
      .isEqualTo("0000000000000002");
    assertThat(context.parentIdString)
      .isEqualTo("0000000000000002");
  }

  @Test void parentIdString_doesNotCacheNull() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(3L).build();

    assertThat(context.parentIdString).isNull();
    assertThat(context.parentIdString()).isNull();
    assertThat(context.parentIdString).isNull();
  }

  @Test void localRootIdString_caches() {
    TraceContext.Builder builder = TraceContext.newBuilder().traceId(1L);
    builder.localRootId = 2L;
    TraceContext context = builder.spanId(3L).build();

    assertThat(context.localRootIdString).isNull();
    assertThat(context.localRootIdString())
      .isNotNull()
      .isEqualTo("0000000000000002");
    assertThat(context.localRootIdString)
      .isEqualTo("0000000000000002");
  }

  @Test void localRootIdString_doesNotCacheNull() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(3L).build();

    assertThat(context.localRootIdString).isNull();
    assertThat(context.localRootIdString()).isNull();
    assertThat(context.localRootIdString).isNull();
  }

  @Test void spanIdString_caches() {
    TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();

    assertThat(context.spanIdString).isNull();
    assertThat(context.spanIdString())
      .isNotNull()
      .isEqualTo("0000000000000002");
    assertThat(context.spanIdString)
      .isEqualTo("0000000000000002");
  }
}
